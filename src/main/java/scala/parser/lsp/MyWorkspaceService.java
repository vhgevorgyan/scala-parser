package scala.parser.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class MyWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}