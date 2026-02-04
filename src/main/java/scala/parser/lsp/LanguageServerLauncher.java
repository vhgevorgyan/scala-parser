package scala.parser.lsp;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class LanguageServerLauncher {

    public static void start() throws Exception {
        MyLanguageServer server = new MyLanguageServer();

        InputStream in = System.in;
        OutputStream out = System.out;

        var launcher = LSPLauncher.createServerLauncher(server, in, out);

        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);

        launcher.startListening();
    }
}
