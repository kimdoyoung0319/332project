package object utils {
  /* IP address of this machine. Convenient for retreiving address in shorthand. */
  val thisAddress: String = java.net.InetAddress.getLocalHost.getHostAddress
}
