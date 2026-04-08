package voice.tools.sftpmetadata

import java.io.PrintStream

/**
 * Debug logger that writes to stderr with clear prefixes.
 * All messages go to [out]; use [verbose] to gate detailed output.
 */
public class VerboseLogger(
  private val out: PrintStream = System.err,
  public var verbose: Boolean = true,
) {

  public fun sftp(msg: String) {
    out.println("[SFTP] $msg")
  }

  public fun scan(msg: String) {
    if (verbose) out.println("[SCAN] $msg")
  }

  public fun parse(msg: String) {
    if (verbose) out.println("[PARSE] $msg")
  }

  public fun ok(msg: String) {
    out.println("[OK] $msg")
  }

  public fun error(msg: String) {
    out.println("[ERROR] $msg")
  }

  public fun error(msg: String, t: Throwable) {
    out.println("[ERROR] $msg")
    if (verbose) t.printStackTrace(out)
    else out.println("  ${t.message}")
  }
}
