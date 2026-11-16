package pitiupi.ppt;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.presentation.shell.LocalNetworkInfo;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Painel do jogo e lógica da partida (protocolo commit-reveal).
 * Todo o estado é mexido só na EDT do Swing.
 */
public class PPTPainel extends JPanel {

    private enum Estado { LIVRE, AGUARDANDO_RESPOSTA, ESCOLHENDO, AGUARDANDO_OPONENTE }

    private static final String[] JOGADAS = {"Pedra", "Papel", "Tesoura"};
    private static final String DICA =
            "Clique em um usuário na lista ao lado e escolha 'Desafiar para Pedra, Papel e Tesoura'";
    private static final long TEMPO_CONVITE = 60_000;  // tempo para o outro responder o convite
    private static final long TEMPO_REVELAR = 15_000;  // a revelação é automática, deve chegar rápido

    private final ConnectionManager cm;
    private final Runnable abrirAba;
    // Uma thread só para enviar: mantém a ordem das mensagens e não trava a tela.
    private final ExecutorService envio = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ppt-envio");
        t.setDaemon(true);
        return t;
    });
    private final SecureRandom random = new SecureRandom();

    // Estado da partida
    private Estado estado = Estado.LIVRE;
    private boolean convidado = false; // true enquanto o diálogo de convite está aberto
    private String opNome;
    private String opId;
    private InetAddress opAddr;
    private int rodada;
    private int placarVoce;
    private int placarOp;
    private long esperandoDesde; // quando comecei a esperar uma resposta automática do oponente

    // Estado da rodada atual
    private String minhaJogada;
    private String meuSalt;
    private String meuHash;
    private boolean jaRevelei;
    private String hashOp;
    private String jogadaOp;
    private String saltOp;

    // Componentes
    private final JLabel opLabel = new JLabel(" ");
    private final JTextArea statusArea = new JTextArea();
    private final JLabel placarLabel = new JLabel("", SwingConstants.CENTER);
    private final JTextArea historico = new JTextArea();
    private final JButton[] botoes = new JButton[JOGADAS.length];
    private final JButton sairBtn = new JButton("Sair da partida");

    public PPTPainel(ConnectionManager cm, Runnable abrirAba) {
        this.cm = cm;
        this.abrirAba = abrirAba;
        montarTela();
        atualizar();
        // a cada 3 s confere se o oponente sumiu ou parou de responder (roda na EDT)
        new Timer(3000, e -> verificarOponente()).start();
    }

    // ---------------------------------------------------------------- tela

    private void montarTela() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // topo: título, oponente e status
        JLabel titulo = new JLabel("Pedra, Papel e Tesoura");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 24f));
        opLabel.setFont(opLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setOpaque(false);
        statusArea.setFont(statusArea.getFont().deriveFont(14f));
        JPanel topo = new JPanel();
        topo.setLayout(new BoxLayout(topo, BoxLayout.Y_AXIS));
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        opLabel.setAlignmentX(LEFT_ALIGNMENT);
        statusArea.setAlignmentX(LEFT_ALIGNMENT);
        topo.add(titulo);
        topo.add(opLabel);
        topo.add(statusArea);
        add(topo, BorderLayout.NORTH);

        // botões das jogadas
        JPanel painelBotoes = new JPanel(new GridLayout(1, 3, 10, 0));
        for (int i = 0; i < JOGADAS.length; i++) {
            String jogada = JOGADAS[i];
            JButton b = new JButton(jogada);
            b.setFont(b.getFont().deriveFont(Font.BOLD, 22f));
            b.addActionListener(e -> escolher(jogada));
            botoes[i] = b;
            painelBotoes.add(b);
        }
        painelBotoes.setPreferredSize(new Dimension(400, 90));

        // placar + histórico
        placarLabel.setFont(placarLabel.getFont().deriveFont(Font.BOLD, 28f));
        historico.setEditable(false);
        historico.setLineWrap(true);
        historico.setWrapStyleWord(true);
        JPanel baixo = new JPanel(new BorderLayout(0, 8));
        baixo.add(placarLabel, BorderLayout.NORTH);
        baixo.add(new JScrollPane(historico), BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.add(painelBotoes, BorderLayout.NORTH);
        centro.add(baixo, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);

        // rodapé
        sairBtn.addActionListener(e -> sair());
        JPanel rodape = new JPanel();
        rodape.add(sairBtn);
        add(rodape, BorderLayout.SOUTH);
    }

    /** Põe na tela o que o estado atual manda mostrar. */
    private void atualizar() {
        boolean escolhendo = estado == Estado.ESCOLHENDO;
        for (JButton b : botoes) {
            b.setEnabled(escolhendo);
        }
        sairBtn.setEnabled(estado != Estado.LIVRE);
        opLabel.setText(opNome == null ? " " : "Oponente: " + opNome);
        placarLabel.setText("Você " + placarVoce + " x " + placarOp + " Oponente");
        switch (estado) {
            case LIVRE -> statusArea.setText(DICA);
            case AGUARDANDO_RESPOSTA -> statusArea.setText("Aguardando resposta de " + opNome + "...");
            case ESCOLHENDO -> statusArea.setText("Rodada " + (rodada + 1) + ": escolha sua jogada!");
            case AGUARDANDO_OPONENTE ->
                    statusArea.setText("Você escolheu " + minhaJogada + ". Esperando o oponente...");
        }
    }

    private void log(String linha) {
        historico.append(linha + "\n");
        historico.setCaretPosition(historico.getDocument().getLength());
    }

    // ------------------------------------------------- ações do usuário

    /** Chamado pela peer action: manda o convite para o peer escolhido. */
    public void desafiar(UserPeerView peer) {
        naEdt(() -> {
            if (peer.isSelf()) {
                statusArea.setText("Você não pode desafiar a si mesmo.");
                return;
            }
            if (estado != Estado.LIVRE || convidado) {
                statusArea.setText("Você já está em uma partida. Saia dela antes de desafiar outro jogador.");
                return;
            }
            Optional<InetAddress> addr = peer.getInetAddress();
            if (addr.isEmpty()) {
                statusArea.setText("Não foi possível descobrir o IP de " + peer.getDisplayName() + ".");
                return;
            }
            opNome = peer.getDisplayName();
            opId = peer.getInstanceId();
            opAddr = addr.get();
            estado = Estado.AGUARDANDO_RESPOSTA;
            esperandoDesde = System.currentTimeMillis();
            atualizar();
            enviar(novaMsg(PPTMensagem.Tipo.CONVITE), opAddr);
        });
    }

    private void escolher(String jogada) {
        if (estado != Estado.ESCOLHENDO) {
            return;
        }
        minhaJogada = jogada;
        meuSalt = novoSalt();
        meuHash = sha256(jogada + ":" + meuSalt);
        estado = Estado.AGUARDANDO_OPONENTE;
        atualizar();
        PPTMensagem m = novaMsg(PPTMensagem.Tipo.COMPROMISSO);
        m.setHash(meuHash);
        enviar(m, opAddr);
        tentarRevelar();
    }

    /** Botão "Sair da partida" e fechar a aba: avisa o oponente e volta ao estado livre. */
    public void sair() {
        naEdt(() -> {
            if (estado == Estado.LIVRE) {
                return;
            }
            if (opAddr != null) {
                enviar(novaMsg(PPTMensagem.Tipo.SAIR), opAddr);
            }
            log("Você saiu da partida.");
            zerar();
        });
    }

    // ---------------------------------------------------- rede (entrada)

    /** Chamado na thread de rede: joga tudo para a EDT. */
    public void receber(PPTMensagem msg) {
        naEdt(() -> processar(msg));
    }

    private void processar(PPTMensagem msg) {
        if (msg.getTipo() == null || msg.getInstanceId() == null) {
            return;
        }
        if (msg.getInstanceId().equals(meuId())) {
            return; // eco da minha própria mensagem
        }
        if (msg.getTipo() == PPTMensagem.Tipo.CONVITE) {
            receberConvite(msg);
            return;
        }
        // as outras só valem se vierem do oponente atual
        if (estado == Estado.LIVRE || !msg.getInstanceId().equals(opId)) {
            return;
        }
        switch (msg.getTipo()) {
            case ACEITE -> {
                if (estado == Estado.AGUARDANDO_RESPOSTA) {
                    iniciarPartida();
                }
            }
            case RECUSA -> {
                if (estado == Estado.AGUARDANDO_RESPOSTA) {
                    String motivo = msg.getTexto() == null ? "" : " (" + msg.getTexto() + ")";
                    String quem = opNome;
                    zerar();
                    statusArea.setText(quem + " recusou o desafio" + motivo + ".");
                    JOptionPane.showMessageDialog(this, quem + " recusou o desafio" + motivo + ".",
                            "Desafio recusado", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            case COMPROMISSO -> {
                if (hashOp == null && msg.getHash() != null) {
                    hashOp = msg.getHash();
                    tentarRevelar();
                }
            }
            case REVELAR -> {
                if (jogadaOp == null && msg.getJogada() != null && msg.getSalt() != null) {
                    jogadaOp = msg.getJogada();
                    saltOp = msg.getSalt();
                    tentarResolver();
                }
            }
            case SAIR -> {
                String quem = opNome;
                log(quem + " saiu da partida.");
                zerar();
                statusArea.setText(quem + " saiu da partida.");
            }
            default -> { }
        }
    }

    private void receberConvite(PPTMensagem msg) {
        InetAddress addr = resolver(msg.getIp());
        if (addr == null) {
            return;
        }
        // os dois se desafiaram ao mesmo tempo: em vez de "ocupado", começa a partida direto
        if (estado == Estado.AGUARDANDO_RESPOSTA && msg.getInstanceId().equals(opId)) {
            enviar(novaMsg(PPTMensagem.Tipo.ACEITE), opAddr);
            iniciarPartida();
            return;
        }
        if (estado != Estado.LIVRE || convidado) {
            PPTMensagem r = novaMsg(PPTMensagem.Tipo.RECUSA);
            r.setTexto("ocupado");
            enviar(r, addr);
            return;
        }
        convidado = true;
        int resp = JOptionPane.showConfirmDialog(this, msg.getNome() + " te desafiou. Aceitar?",
                "Desafio: Pedra, Papel e Tesoura", JOptionPane.YES_NO_OPTION);
        convidado = false;
        if (resp == JOptionPane.YES_OPTION && estado == Estado.LIVRE) {
            opNome = msg.getNome();
            opId = msg.getInstanceId();
            opAddr = addr;
            enviar(novaMsg(PPTMensagem.Tipo.ACEITE), addr);
            iniciarPartida();
        } else {
            PPTMensagem r = novaMsg(PPTMensagem.Tipo.RECUSA);
            r.setTexto(resp == JOptionPane.YES_OPTION ? "ocupado" : "recusou");
            enviar(r, addr);
        }
    }

    // ------------------------------------------------ lógica da partida

    private void iniciarPartida() {
        estado = Estado.ESCOLHENDO;
        rodada = 0;
        placarVoce = 0;
        placarOp = 0;
        limparRodada();
        log("=== Partida contra " + opNome + " ===");
        atualizar();
        abrirAba.run();
    }

    /** Só revela depois de ter enviado o meu compromisso E recebido o do oponente. */
    private void tentarRevelar() {
        if (meuHash != null && hashOp != null && !jaRevelei) {
            jaRevelei = true;
            esperandoDesde = System.currentTimeMillis();
            PPTMensagem m = novaMsg(PPTMensagem.Tipo.REVELAR);
            m.setJogada(minhaJogada);
            m.setSalt(meuSalt);
            enviar(m, opAddr);
            tentarResolver();
        }
    }

    /** Fecha a rodada quando já revelei e já tenho o compromisso e a revelação do oponente. */
    private void tentarResolver() {
        if (!jaRevelei || hashOp == null || jogadaOp == null) {
            return;
        }
        boolean valida = ehJogada(jogadaOp) && sha256(jogadaOp + ":" + saltOp).equals(hashOp);
        if (!valida) {
            log("Trapaça detectada! " + opNome + " mandou uma jogada que não bate com o compromisso. "
                    + "A rodada não conta.");
        } else {
            rodada++;
            String resultado;
            if (minhaJogada.equals(jogadaOp)) {
                resultado = "Empate!";
            } else if (ganha(minhaJogada, jogadaOp)) {
                placarVoce++;
                resultado = "Você venceu!";
            } else {
                placarOp++;
                resultado = "Você perdeu!";
            }
            log("Rodada " + rodada + ": você " + minhaJogada + " x " + jogadaOp + " oponente — " + resultado);
        }
        limparRodada();
        estado = Estado.ESCOLHENDO;
        atualizar();
    }

    private static boolean ganha(String a, String b) {
        return (a.equals("Pedra") && b.equals("Tesoura"))
                || (a.equals("Tesoura") && b.equals("Papel"))
                || (a.equals("Papel") && b.equals("Pedra"));
    }

    private static boolean ehJogada(String j) {
        for (String s : JOGADAS) {
            if (s.equals(j)) {
                return true;
            }
        }
        return false;
    }

    private void limparRodada() {
        minhaJogada = null;
        meuSalt = null;
        meuHash = null;
        jaRevelei = false;
        hashOp = null;
        jogadaOp = null;
        saltOp = null;
    }

    /** Volta ao estado livre (o placar e o histórico ficam na tela). */
    private void zerar() {
        estado = Estado.LIVRE;
        opNome = null;
        opId = null;
        opAddr = null;
        limparRodada();
        atualizar();
    }

    /** Chamado pelo timer: encerra a partida se o oponente saiu da rede ou não respondeu a tempo. */
    private void verificarOponente() {
        if (estado == Estado.LIVRE) {
            return;
        }
        String quem = opNome;
        long esperando = System.currentTimeMillis() - esperandoDesde;
        String motivo = null;
        if (!oponenteOnline()) {
            motivo = quem + " desconectou.";
        } else if (estado == Estado.AGUARDANDO_RESPOSTA && esperando > TEMPO_CONVITE) {
            motivo = quem + " não respondeu ao desafio.";
        } else if (jaRevelei && jogadaOp == null && esperando > TEMPO_REVELAR) {
            motivo = quem + " parou de responder.";
        }
        if (motivo == null) {
            return;
        }
        enviar(novaMsg(PPTMensagem.Tipo.SAIR), opAddr); // se ele ainda estiver lá, fica sabendo
        log(motivo + " Partida encerrada.");
        zerar();
        statusArea.setText(motivo);
    }

    /** O oponente ainda aparece na lista de usuários do Pitiupi (heartbeat)? */
    private boolean oponenteOnline() {
        for (UserPeerView p : cm.getPresenceService().listPeerViews()) {
            if (opId != null && opId.equals(p.getInstanceId())) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------- utilidades

    private String meuId() {
        return cm.getHeartbeatManager().getInstanceId();
    }

    private PPTMensagem novaMsg(PPTMensagem.Tipo tipo) {
        return new PPTMensagem(tipo, cm.getUserName(),
                PeerIpv4.normalize(LocalNetworkInfo.bestLocalIpv4()), meuId());
    }

    private void enviar(PPTMensagem msg, InetAddress destino) {
        envio.execute(() -> {
            int enviadas = MessageDispatcher.send(cm, msg, SendTarget.singlePeer(destino), TransportMode.TCP);
            if (enviadas == 0) {
                SwingUtilities.invokeLater(() ->
                        log("Falha ao enviar " + msg.getTipo() + " para " + destino.getHostAddress() + "."));
            }
        });
    }

    private static InetAddress resolver(String ip) {
        try {
            return ip == null ? null : InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private String novoSalt() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String sha256(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 sempre existe no Java
        }
    }

    private static void naEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
