"""Контракт шага CI с предзагрузкой kotlin-compiler-embeddable (ADR-0098).

Проверяется не текст ради текста, а инварианты, из-за нарушения которых
появилась находка F-03 (Issue #195):

* версия компилятора не дублируется литералом в workflow, а читается из
  `gradle/libs.versions.toml` — иначе она снова разойдётся с проектом;
* для текущей версии из каталога существует пин SHA-256, и пин совпадает с
  хешем, посчитанным от реального артефакта (когда артефакт доступен локально);
* неизвестная версия приводит к падению шага, а не к молчаливой подмене.
"""
import hashlib
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "gradle" / "libs.versions.toml"
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"

STEP_NAME = "Verified Kotlin compiler"
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def catalog_kotlin_version(text):
    """Версия Kotlin из каталога версий; None, если строка отсутствует."""
    match = re.search(r'^kotlin\s*=\s*"([^"]+)"\s*$', text, re.MULTILINE)
    return match.group(1) if match else None


def step_block(workflow):
    """Тело шага предзагрузки компилятора (от `- name:` до следующего `- name:`)."""
    lines = workflow.splitlines()
    start = None
    for i, line in enumerate(lines):
        if STEP_NAME in line and line.lstrip().startswith("- name:"):
            start = i
            break
    if start is None:
        return None
    end = len(lines)
    indent = len(lines[start]) - len(lines[start].lstrip())
    for j in range(start + 1, len(lines)):
        stripped = lines[j].lstrip()
        if stripped.startswith("- ") and (len(lines[j]) - len(stripped)) == indent:
            end = j
            break
    return "\n".join(lines[start:end])


def pins(block):
    """Пины `версия) expected="sha256"` из case-блока шага."""
    return dict(re.findall(r'^\s*([0-9][0-9.\w-]*)\)\s*expected="([0-9a-f]{64})"\s*;;\s*$',
                           block, re.MULTILINE))


class CompilerMirrorContractTest(unittest.TestCase):
    def setUp(self):
        self.catalog = CATALOG.read_text(encoding="utf-8")
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        self.block = step_block(self.workflow)

    def test_step_exists(self):
        self.assertIsNotNone(self.block, f"шаг «{STEP_NAME}» не найден в ci.yml")

    def test_version_is_read_from_catalog(self):
        self.assertIn("gradle/libs.versions.toml", self.block,
                      "версия должна читаться из каталога, а не задаваться литералом")
        self.assertIn("awk", self.block)

    def test_no_hardcoded_compiler_version(self):
        # Литерал вида kotlin-compiler-embeddable/2.4.21 или ...-2.4.21.jar
        # вернул бы дрейф версии, ради устранения которого написан ADR-0098.
        hardcoded = re.findall(r"kotlin-compiler-embeddable[-/](\d+\.\d+\.\d+)", self.block)
        self.assertEqual([], hardcoded,
                         f"в шаге остались литералы версии: {sorted(set(hardcoded))}")

    def test_current_version_has_a_pin(self):
        version = catalog_kotlin_version(self.catalog)
        self.assertIsNotNone(version, "в каталоге нет версии kotlin")
        table = pins(self.block)
        self.assertIn(version, table,
                      f"нет пина SHA-256 для текущей версии {version}: обновление Kotlin "
                      f"должно добавлять пин в том же коммите")

    def test_pins_are_well_formed(self):
        table = pins(self.block)
        self.assertTrue(table, "не найдено ни одного пина SHA-256")
        for version, digest in table.items():
            self.assertRegex(version, r"^\d+\.\d+\.\d+$")
            self.assertRegex(digest, HEX64)

    def test_unknown_version_fails_loudly(self):
        self.assertIn("*)", self.block, "нет ветки отказа для неизвестной версии")
        tail = self.block[self.block.index("esac"):] if "esac" in self.block else ""
        self.assertIn("::error::", tail.split("esac")[-1],
                      "неизвестная версия должна давать ::error:: до скачивания")
        self.assertIn("exit 1", tail.split("esac")[-1])

    def test_pin_matches_local_artifact_when_available(self):
        """Если артефакт есть в кэше Gradle, пин обязан совпасть с его хешем."""
        version = catalog_kotlin_version(self.catalog)
        expected = pins(self.block).get(version)
        if expected is None:
            self.skipTest("для текущей версии нет пина")
        cache = pathlib.Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
        jar = next(cache.glob(
            f"org.jetbrains.kotlin/kotlin-compiler-embeddable/{version}/*/"
            f"kotlin-compiler-embeddable-{version}.jar"), None)
        if jar is None:
            self.skipTest(f"артефакт {version} отсутствует в локальном кэше Gradle")
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        self.assertEqual(expected, digest,
                         f"пин {version} не совпадает с SHA-256 локального артефакта")


if __name__ == "__main__":
    unittest.main()
