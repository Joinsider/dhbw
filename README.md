This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

At the moment only Android and Desktop application is provided as iOS will cost me 99€ per year to get a Apple Developer license. Maybe I will add this in the future.
Also the mac version is currently not signed by me as this also requires an Apple Developer account.

# Releases:
You can find the current version under [Releases](https://github.com/Joinsider/dhbw-next/releases)

# Installation:

## Android:
## Where can you install my app?
- Google Play Store: [here](https://play.google.com/store/apps/details?id=de.fampopprol.dhbwhorb&pcampaignid=web_share)
- F-Droid: Released in the IzzyOnDroid repository, you can find it [here](https://apt.izzysoft.de/packages/de.fampopprol.dhbwhorb)

[<img height="80px" alt="image" src="https://github.com/user-attachments/assets/0731327c-7867-4b7e-b981-e07f38071304" />](https://play.google.com/store/apps/details?id=de.fampopprol.dhbwhorb&pcampaignid=web_share)
[<img src="./assets/izzyOnDroid.png" alt="IzzyOnDroid" height="80px">](https://apt.izzysoft.de/packages/de.fampopprol.dhbwhorb)
[<img height="80px" alt="image" src="https://github.com/user-attachments/assets/eb4410df-5e0a-4851-bf6b-6f951ffa2a9c" />](https://github.com/Joinsider/dhbw/releases)



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

## MacOS

Follow the guide for macOS [here](documentation/macOS/README.md)
