/**
  * The entry point of the program.
  */
object Main extends App {
  // Provide filename by command-line arguments
  val filename = args.headOption.getOrElse("src/main/resources/settings.json")
  // Create application instance
  val application = new MyApplication(filename)
  // Run it
  application.run()
  // Generate account in your wallet
  if (application.wallet.privateKeyAccounts().isEmpty) application.wallet.generateNewAccounts(1)
}
