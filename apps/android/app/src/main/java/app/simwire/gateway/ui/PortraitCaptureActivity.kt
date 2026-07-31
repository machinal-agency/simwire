package app.simwire.gateway.ui

import com.journeyapps.barcodescanner.CaptureActivity

/** ZXing's default capture activity is landscape; this one stays portrait. */
class PortraitCaptureActivity : CaptureActivity()
