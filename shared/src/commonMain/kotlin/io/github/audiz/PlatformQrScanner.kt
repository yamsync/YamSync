package io.github.audiz

expect val isPlatformQrScannerSupported: Boolean

expect fun launchPlatformQrScanner(onScanned: (String) -> Unit)
