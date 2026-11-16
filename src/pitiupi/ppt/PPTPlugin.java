package pitiupi.ppt;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import java.util.List;

public class PPTPlugin extends Plugin {

    private PPTPainel painel;
    private PluginWorkspace workspace;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.painel = new PPTPainel(connectionManager,
                () -> workspace.openOrFocusTab(getIdentifier(), getDisplayName()));
        bindShellWorkspaceContent(painel);
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "ppt.desafiar",
                "Desafiar para Pedra, Papel e Tesoura",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    if (target instanceof PeerTarget.Single single) {
                        workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                        painel.desafiar(single.peer());
                    }
                }
        ));
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(PPTMensagem.class);
    }

    // Roda na thread de rede: o painel repassa tudo para a EDT.
    @Override
    public void receiveMessage(Message message) {
        if (message instanceof PPTMensagem msg && painel != null) {
            painel.receber(msg);
        }
    }

    @Override
    public void onShellTabClosed() {
        if (painel != null) {
            painel.sair();
        }
    }

    @Override
    public String getName() {
        return "PedraPapelTesoura";
    }

    @Override
    public String getDisplayName() {
        return "Pedra, Papel e Tesoura";
    }

    @Override
    public String getDescription() {
        return "Jogo 1:1 de Pedra, Papel e Tesoura com compromisso por hash.";
    }

    @Override
    public String getAuthor() {
        return "Samuel Crístian Amaral das Neves";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getIdentifier() {
        return "Pitiupi.PPT";
    }
}
