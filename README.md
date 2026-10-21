This is a Kotlin Multiplatform project targeting Android, iOS, macOS and Desktop (JVM: Linux and Windows).

Android, Windows, Linux and macOS builds are provided. The iOS and macOS app is a native SwiftUI app
(see `documentation/macOS/README.md` for the Mac distribution channels).

# Releases:
You can find the current version under [Releases](https://github.com/Joinsider/dhbw-next/releases)

# Installation:

## Android:
## Where can you install my app?
- Google Play Store: [here](https://play.google.com/store/apps/details?id=de.fampopprol.dhbwhorb&pcampaignid=web_share)
- F-Droid: Released in the IzzyOnDroid repository, you can find it [here](https://apt.izzysoft.de/packages/de.fampopprol.dhbwhorb)

[<img height="80px" alt="Downlaod from Google Play" src="https://github.com/user-attachments/assets/0731327c-7867-4b7e-b981-e07f38071304" />](https://play.google.com/store/apps/details?id=de.fampopprol.dhbwhorb&pcampaignid=web_share)
[<img src="./assets/izzyOnDroid.png" alt="Download from izzyOnDroid" height="80px">](https://apt.izzysoft.de/packages/de.fampopprol.dhbwhorb)
[<img height="80px" alt="Download from Github Releases" src="https://github.com/user-attachments/assets/eb4410df-5e0a-4851-bf6b-6f951ffa2a9c" />](https://github.com/Joinsider/dhbw/releases)
[<img alt="Download_on_the_App_Store" src="https://github.com/user-attachments/assets/84694c28-2d4d-43c8-9dd7-caaf0e562a83" />](https://apps.apple.com/de/app/dhbw-horb-studenten-app/id6775451278?l=de-DE)





[<img src="https://shields.rbtlog.dev/simple/de.fampopprol.dhbwhorb" alt="RB shield">](https://shields.rbtlog.dev/de.fampopprol.dhbwhorb)



## Windows
You can always download the current .msi file from the releases tab or you can download it through the [Microsoft Store](https://apps.microsoft.com/detail/9pl3rffqhmqb?ocid=webpdpshare). 

**!!! I must add a warning: Every desktop version stores your password in the current OS' keyring. On Windows only .msix packages (MS Store version of dhbw-next) are truly secure as other apps don't have access to the applications' secrets!!!**

## Linux

### Arch (AUR)
You can download the app using your favorite AUR package manager e.g. `paru` or `yay`

```shell
yay -Syu

yay -S dhbw-next
```

To update the application run this command:
```shell
yay --devel
```
The `--devel` option is required as the application gets compiled directly from the git repositories current commit.

### Debian
Follow the guide on [this page](https://dhbw-next.joinside.de) to install the apt repository to your debian system. This way you will always get the current version. 

## macOS

The Mac app is a native SwiftUI app for Apple Silicon Macs (macOS 26 or later). It is distributed as the
macOS platform of the iOS app on the Mac App Store, and as a notarized `.dmg` on the
[Releases](https://github.com/Joinsider/dhbw-next/releases) page. Details are in
[documentation/macOS/README.md](documentation/macOS/README.md).
