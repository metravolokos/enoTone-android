# R-A2, строка аудита «Стек-трейсы»: retrace использует номера строк и атрибут SourceFile.
-keepattributes SourceFile,LineNumberTable

# R-A2, строка аудита «Стек-трейсы»: скрывает исходные имена файлов, сохраняя номера строк.
-renamesourcefileattribute SourceFile
