package de.fampopprol.dhbwhorb.util

import platform.UIKit.UIViewController

object UIRootViewControllerHelper {
    var getViewController: (() -> UIViewController?)? = null
}
