# Temporary guide for homebrew installation

At the moment I don't have a apple developer account, which would cost me about 99€ per Month. But you can still use my application on your Mac.

## Download and Install:
The best and easiest way to install my application is through the Homebrew Package Manager.

```shell
brew tap joinsider/apps
brew install dhbw-next --cask
```
Afterwards you could just run the application however this results in multiple notifications by the macos keychain to enter your mac's password on each restart of the app. 
To combat this run these commands in your terminal to generate a new self-signed certificate to sign the application locally. These two commands need to be run on every new install of my application until I get a apple developer account.

### Sign application
1. Download the script sign-dhbw-next.sh from the repo under documentation/macOS/sign-dhbw-next.sh [here](sign-dhbw-next.sh)
2. Open the terminal on your mac
3. Enter the following commands:
```shell
cd Downloads
chmod +x sign-dhbw-next.sh
./sign-dhbw-next.sh
```
4. Follow the instructions in the script to generate a new certificate or use an existing one.
5. Open the app afterwards

## Opening the application for the first time
Since this app is not signed with an Apple Developer certificate, 
you'll need to allow it to run:

### macOS Sequoia (15.0+)
1. Download and open the DMG file
2. Drag the app to your Applications folder
3. Try to open the app (it will be blocked)
4. Open **System Settings → Privacy & Security**
5. Scroll to the bottom of the Security section
6. Click **Open Anyway** next to the blocked app message
7. Confirm you want to open it

### macOS Sonoma and earlier
1. Download and open the DMG file
2. Drag the app to your Applications folder
3. **Right-click** (or Control-click) the app and select **Open**
4. Click **Open** in the warning dialog
5. The app will now open (you only need to do this once)

### Alternative: Remove Quarantine (Advanced Users)
Open Terminal and run:
```shell
xattr -cr /Applications/dhbw-next.app
```
