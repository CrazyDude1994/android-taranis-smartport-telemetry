package crazydude.com.telemetry.utils

import java.io.OutputStream

class NullOutputStream : OutputStream() {
    override fun write(b: Int) {
        // do nothing
    }
}