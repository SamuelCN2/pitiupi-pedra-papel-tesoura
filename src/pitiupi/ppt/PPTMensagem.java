package pitiupi.ppt;

import pitiupi.models.Messages.Message;

/**
 * Única mensagem do plugin Pedra, Papel e Tesoura.
 * O campo "tipo" diz o que a mensagem significa; os outros campos
 * só são preenchidos quando o tipo precisa deles (veja PROTOCOLO.md).
 */
public class PPTMensagem extends Message {

    private static final long serialVersionUID = 1L;

    public enum Tipo {
        CONVITE, ACEITE, RECUSA, COMPROMISSO, REVELAR, SAIR
    }

    private Tipo tipo;
    private String nome;       // nome de quem enviou
    private String ip;         // IP de quem enviou
    private String instanceId; // identificador da instância de quem enviou
    private String hash;       // COMPROMISSO: SHA-256(jogada + ":" + salt)
    private String jogada;     // REVELAR: Pedra, Papel ou Tesoura
    private String salt;       // REVELAR: salt usado no hash
    private String texto;      // RECUSA: motivo (opcional)

    public PPTMensagem() {
    }

    public PPTMensagem(Tipo tipo, String nome, String ip, String instanceId) {
        this.tipo = tipo;
        this.nome = nome;
        this.ip = ip;
        this.instanceId = instanceId;
    }

    public Tipo getTipo() { return tipo; }
    public String getNome() { return nome; }
    public String getIp() { return ip; }
    public String getInstanceId() { return instanceId; }
    public String getHash() { return hash; }
    public String getJogada() { return jogada; }
    public String getSalt() { return salt; }
    public String getTexto() { return texto; }

    public void setHash(String hash) { this.hash = hash; }
    public void setJogada(String jogada) { this.jogada = jogada; }
    public void setSalt(String salt) { this.salt = salt; }
    public void setTexto(String texto) { this.texto = texto; }
}
