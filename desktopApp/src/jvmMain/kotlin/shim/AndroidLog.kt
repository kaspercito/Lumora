@file:Suppress("PackageDirectoryMismatch")

package android.util

/** Minimal android.util.Log so plugin code that logs keeps working on the JVM. */
object Log {
    @JvmStatic fun v(tag: String?, msg: String?): Int { println("VERBOSE $tag: $msg"); return 0 }
    @JvmStatic fun d(tag: String?, msg: String?): Int { println("DEBUG $tag: $msg"); return 0 }
    @JvmStatic fun i(tag: String?, msg: String?): Int { println("INFO $tag: $msg"); return 0 }
    @JvmStatic fun w(tag: String?, msg: String?): Int { println("WARNING $tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String?, msg: String?): Int { System.err.println("ERROR $tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int { System.err.println("ERROR $tag: $msg $tr"); return 0 }
}
