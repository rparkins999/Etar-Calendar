![Etar Calendar](metadata/en-US/images/featureGraphic.png)
# Etar Calendar
Etar (from Arabic:  `إِيتَار`)  is an open source material designed calendar made for everyone!

This is version 1.2.

It is only available from this github repository. It has various modifications which I (rparkins999) consider to be User Interface improvements.

![Etar Calendar](metadata/animation.gif)

## Why?
Well, I wanted a simple, material designed and state of the art open source calendar that anyone can make better.

## Special thanks

The application is an enhanced version of AOSP Calendar. Without the help of
[Free Software for Android](https://github.com/Free-Software-for-Android/Standalone-Calendar) team, 
this app would be just a dream. So thanks to them!

## Features
- Month view.
- Week, day & agenda view.
- Uses Android calendar sync. Works with Google Calendar, Exchange, etc.
- Material designed.
- Support offline calendar
- -Agenda widget.- (disabled until #373 and #374 are fixed. Use [Calendar Widget](https://f-droid.org/de/packages/com.plusonelabs.calendar/) as an alternative.)

## How to use Etar Calendar
Store your calendar on the phone only:
  - Create an offline calendar.

Sync your calendar to a server:
  - A cloud-synched calendar could be a google calendar, but you can also use
  any other public Caldav-server or even host your own (which would be the
  only way to keep full control over your data and still have ONE calendar
  usable from different devices.) To sync such a calendar to some server you
  need yet another app, e. g. DAVx5. That’s necessary because a Caldav client
  isn't included in Etar.

  The following [link](https://ownyourbits.com/2017/12/30/sync-nextcloud-tasks-calendars-and-contacts-on-your-android-device/) provides a tutorial how to use Nextcloud + DAVx5 + Etar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Etar.
You can even configure in Etar which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Etar requires
- READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE  
->import and export ics calendar files  
- READ_CONTACTS  
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events

## Contribute
### Translations
Interested in helping to translate this version of Etar Calendar? Fork this repository, add your translations, and submit a pull request.

##### Google Play app description:
You can update/add your own language and all artwork files [here](metadata)

### Build instructions
Install and extract Android SDK command line tools.

Current Android versions will only install signed applications. A debug build is automatically signed, but if you want to make a release build, you have to sign it. I'm not giving you my signing key, but the build expects one. You need the following steps:-

Create a signing key

Create a file ```<project root>/../Keys/keystore.properties``` that looks like this:-<br><br>
```keyAlias=<name of your key>```<br>
```keyPassword=<key password>```<br>
```storeFile==<location of signing key file>```<br>
```storePassword=<key store password>```<br><br>
If you want to keep your ```keystore.properties``` file somewhere else or give it some other name, edit the top level ```build.gradle```, where its location is specified. If you want to put your version on Google Play Store, the signing mechanism is different, refer to their documentation. If you want to put it on F-Droid, they will build and sign it.<br><br>
If you build with Android Studio, you will need to create a ```local.properties``` file which points to the location of the SDK, and a build configuration. The following instructions are for a command line build, and may be out of date since I use Android Studio.<br><br>
```tools/bin/sdkmanager platform-tools```<br>
```export ANDROID_HOME=/path/to/android-sdk/```<br>
```git submodule update --init```<br>
```gradle :app:assembleDebug```<br><br>
## License

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

Copyright (c) 2020-, Richard Parkins

Licensed under the GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
Except where otherwise noted.

Google Play and the Google Play logo are trademarks of Google Inc.
