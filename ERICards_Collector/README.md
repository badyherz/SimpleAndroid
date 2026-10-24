# ERICards Collector

An Android app to look up and collect ERICards (emergency response cards for dangerous goods), UN numbers, ADR classes and GHS hazard and precautionary statements, and to keep track of what you have seen.

* Android 13 (API 33) or newer, phones and tablets
* Light and dark mode (follows the system)
* Works completely offline. The app asks for no permissions and has no internet access.
* Glossary and H/EUH/P statements in English, German, Spanish and French

Part of [SimpleAndroid](https://github.com/badyherz/SimpleAndroid).

## Download

Signed APK files are on the [releases page](https://github.com/badyherz/SimpleAndroid/releases). Download the `.apk` on your phone and open it. Android asks once to allow installing apps from your browser or file manager.

## First start

The app contains no ERICards database. Open **Settings > Update Database...** (or tap *Load Database...* on the first screen) and choose a database file in the format described below.

The glossary and the H/EUH/P statements come with the app. **Settings > Update Reference Texts...** loads newer texts from a file in the format of [`app/src/main/assets/reference_texts.json`](app/src/main/assets/reference_texts.json).

### Database file

A pre-compiled file can be found [here](https://github.com/badyherz/SimplePrograms/tree/618288c5a0238ac0e10d0c75a1e525040395f2f6/programs/SP_ERICards_collector/database_and_refernce_text)!
You can also use my other program to make your own file or create your own database.

Updating the database again lets you either replace it or merge new and changed cards into it. The collection is not affected.

## Your collection and backups

Your collection is stored in the app's private storage. Uninstalling the app deletes it. To keep a copy, open **Settings > Backup** and choose a folder outside the app (for example a new folder inside *Documents*, or a folder that a sync app such as Nextcloud or Syncthing uploads). The app then saves a backup file there whenever you leave it and the collection has changed, and keeps the newest 14. To restore, use **Settings > Import Collection...** and choose one of the files.

## Build it yourself

You need [Android Studio](https://developer.android.com/studio) (current stable version) and [Git](https://git-scm.com/).

1. Get the sources:

   ```
   git clone https://github.com/badyherz/SimpleAndroid.git
   ```

2. In Android Studio choose **File > Open** and select the folder `SimpleAndroid/ERICards_Collector` (the one that contains `settings.gradle.kts`). Trust the project and wait until the Gradle sync has finished. If Android Studio offers to install missing SDK components, accept.
3. Start the app with the green triangle, on an emulator (**Tools > Device Manager**) or on a phone with USB debugging or Wireless debugging turned on.

To get an APK file:

* **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or on the command line `./gradlew assembleDebug` (`gradlew.bat assembleDebug` on Windows). The file is created in `app/build/outputs/apk/debug/`.
* A debug APK is signed with a debug key. Releases are signed with the maintainer's key, so a self-built APK cannot be installed over a downloaded release (uninstall one before installing the other; export your collection first).

### Own pictures for the labels

ADR labels and GHS pictograms are drawn by the app unless picture files are present. To use pictures, copy them into `app/src/main/assets/icons/` before building. File names are listed in [docs/icons.md](docs/icons.md).

### Afterword 

I am not affiliated, associated, authorized, endorsed by, or in any way officially connected with The European Chemical Industry Council or any of its subsidiaries or its affiliates. The European Chemical Industry Council website can be found at https://cefic.org/

The name European Chemical Industry Council as well as related names, marks, emblems and images are registered trademarks of their respective owners.

All information that is found in the database files can be found at https://www.ericards.net/
