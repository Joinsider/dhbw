Looking at your [releases page](https://github.com/Joinsider/dhbw-next/releases), I can see the version numbering is inconsistent (v1.1.0 is actually older than v1.0.6). Here's how the AUR workflow works for `-git` packages and what you need to know:[1]

### How `-git` Packages Work on AUR

**Initial Push** - When you first push your PKGBUILD to the AUR, the `pkgver` value in the PKGBUILD is just a placeholder. Set it to something like `pkgver=1.0.6` initially.[2][3]

**User Installation** - When users install your package, `makepkg` automatically runs the `pkgver()` function, which fetches the latest git commit and generates the actual version number (like `1.0.6.r15.g366767b`).[3][2]

**Version Display on AUR** - The AUR webpage will show whatever version is in your `.SRCINFO` file, which only updates when you manually push changes to the AUR repository. This is intentional and **not a problem**.[3]

**Automatic Updates for Users** - AUR helpers like `yay` and `paru` can detect updates to `-git` packages when run with `--devel` flag (e.g., `yay -Syu --devel`). They track commit hashes and automatically rebuild when new commits are detected.[4][3]

### What You Should NOT Do

According to the AUR guidelines, **do not commit mere `pkgver` bumps for VCS packages**. This means:[1][3]
- Don't update the PKGBUILD just because you pushed new commits to GitHub
- Don't change `pkgver` or `pkgrel` when only the upstream git has new commits
- The AUR page showing an "outdated" version is **expected behavior** for `-git` packages

### When You SHOULD Update the AUR

Only push updates to your AUR repository when:[5][2][3]
- Dependencies change (new or removed packages in `depends`, `makedepends`, etc.)
- Build process changes (modifications to `build()` or `package()` functions)
- Source URL changes
- Package metadata changes (description, URL, conflicts, etc.)
- The PKGBUILD is broken and needs fixes

When you do update, increment `pkgrel` by 1 and regenerate `.SRCINFO`:

```bash
# After editing PKGBUILD
makepkg --printsrcinfo > .SRCINFO
git add PKGBUILD .SRCINFO
git commit -m "Update dependencies" # meaningful message
git push
```

### How Users Get Updates

**Daily/regular git commits**: Users run `yay -Syu --devel` or `paru -Sua --devel`[4][3]

**PKGBUILD changes**: Users get notified through normal `yay -Syu` workflow[2]

### Summary

For your situation: Push your PKGBUILD once with `pkgver=1.0.6`. Users will automatically get the latest git version when they install. You only need to update the AUR repository when you change the build process, dependencies, or fix issues - not for every git commit you make.[1][2][3]

[1](https://github.com/Joinsider/dhbw-next/releases)
[2](https://bbs.archlinux.org/viewtopic.php?id=298464)
[3](https://www.reddit.com/r/archlinux/comments/z3gjz0/how_does_aur_page_update_to_actual_version_of_git/)
[4](https://forum.endeavouros.com/t/suggestion-of-packages-for-the-endeavouros-repo/21905?page=2)
[5](https://wiki.archlinux.org/title/VCS_package_guidelines)
[6](https://github.com/AladW/aurutils/issues/227)
[7](https://blog.ja-ke.tech/2020/04/29/aur-pkgbuild-update.html)
[8](https://github.com/watzon/aur-packages)
[9](https://github.com/Jguer/yay/issues/2390)
[10](https://github.com/arch4edu/aur-auto-update)
[11](https://bbs.archlinux.org/viewtopic.php?id=159899)