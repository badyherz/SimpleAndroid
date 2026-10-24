# ADR labels and GHS pictograms

The app draws a simple diamond for every label. To use your own pictures, copy them into

```
app/src/main/assets/icons/
```

and build the app again. The names are the same as in the desktop program, so the contents of its `icons` folder can be copied as it is. Upper and lower case do not matter.

| Picture | File name |
| --- | --- |
| ADR label of class *X* | `adr_<X>.png`, for example `adr_3.png`, `adr_2.1.png` |
| GHS pictogram | `ghs01.png` ... `ghs09.png` |

The classes that occur in the current English database:

```
adr_1.png    adr_1.4.png  adr_1.5.png  adr_1.6.png
adr_2.1.png  adr_2.2.png  adr_2.3.png  adr_3.png
adr_4.1.png  adr_4.2.png  adr_4.3.png  adr_5.1.png
adr_5.2.png  adr_6.1.png  adr_6.2.png  adr_7.png
adr_8.png    adr_9.png
ghs01.png ghs02.png ghs03.png ghs04.png ghs05.png ghs06.png ghs07.png ghs08.png ghs09.png
```

* A label without a file is drawn as before, so a few missing files do no harm. A card of class 1.4 only shows a picture if `adr_1.4.png` exists; `adr_1.png` is not used for it (same as in the desktop program).
* Square PNG files with a transparent background look best. Large pictures are scaled down when they are loaded; about 400 x 400 pixels is plenty. The pictures are shown 88 to 96 dp wide.
* Only use pictures you are allowed to distribute, because they become part of the APK on GitHub.
