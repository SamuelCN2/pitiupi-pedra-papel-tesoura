# Pedra, Papel e Tesoura - Plugin para o Pitiupi

Plugin de Pedra, Papel e Tesoura para o Pitiupi, com comunicação individual (1:1) entre dois jogadores.

As mensagens são enviadas diretamente de um peer para o outro via TCP. Para impedir que um jogador veja a jogada do outro antes de escolher a sua, cada jogador envia primeiro um hash SHA-256 da jogada e só depois revela a jogada, que é conferida pelo outro lado. Os detalhes estão em [PROTOCOLO.md](PROTOCOLO.md).

## Funcionalidades

- Opção "Desafiar para Pedra, Papel e Tesoura" no menu de cada usuário da lista
- Aceitar ou recusar desafios (recusa automática se o jogador já estiver em partida)
- Várias rodadas seguidas, com placar e histórico
- Detecção de trapaça: se a jogada revelada não bate com o hash, a rodada é anulada
- Botão "Sair da partida", que avisa o oponente
- Encerramento automático da partida se o oponente sair da rede
- Timeout de 60 s para responder o convite e de 15 s para a revelação da jogada
- Desafios simultâneos entre os mesmos jogadores iniciam a partida direto

## Arquivos

- `src/pitiupi/ppt/PPTPlugin.java`: classe principal do plugin
- `src/pitiupi/ppt/PPTMensagem.java`: mensagem trocada pela rede
- `src/pitiupi/ppt/PPTPainel.java`: interface e lógica da partida
- `src/META-INF/services/pitiupi.models.Plugin`: registro do plugin para o ServiceLoader
- `build.xml` e `nbproject/`: projeto Ant/NetBeans
- `PROTOCOLO.md`: descrição do protocolo

## Requisitos

- JDK 17 ou superior
- Apache Ant
- Código-fonte do Pitiupi (o plugin compila usando o núcleo em `../../pitiupi`)

## Instalação

1. Clonar este repositório dentro da pasta `plugins` do Pitiupi:

```bash
cd <pasta-do-pitiupi>/plugins
git clone <link-deste-repositorio> ppt
```

2. Adicionar a entrada abaixo na lista `plugins` do arquivo `plugins.registry.json`:

```json
{
  "id": "ppt",
  "dir": "plugins/ppt",
  "build": "ant",
  "jar": "dist/pitiupi.ppt.jar",
  "menuCategory": "Jogos"
}
```

## Compilação e execução

Linux:

```bash
./scripts/pitiupi.sh run --plugins ppt
```

Windows:

```bat
scripts\pitiupi.cmd run --all
```

Compilação manual:

```bash
cd pitiupi
ant jar
cd ../plugins/ppt
ant jar
```

O jar é gerado em `plugins/ppt/dist/pitiupi.ppt.jar` e deve ser copiado para `.pitiupi/plugins/`.

## Como jogar

1. Abrir o Pitiupi em dois computadores da mesma rede, com a mesma porta e nomes diferentes
2. Aguardar o outro usuário aparecer na lista (até 15 s)
3. Clicar no nome do oponente e escolher "Desafiar para Pedra, Papel e Tesoura"
4. O oponente aceita o desafio
5. Cada jogador escolhe Pedra, Papel ou Tesoura
6. Após as duas escolhas, o resultado aparece no histórico e o placar é atualizado
7. Para encerrar, clicar em "Sair da partida"

Regras: pedra ganha de tesoura, tesoura ganha de papel e papel ganha de pedra. Escolhas iguais empatam.

## Testes

O plugin precisa de dois computadores para ser testado, porque o Pitiupi não permite conexão TCP com o próprio host.

Casos de teste: desafio aceito e recusado, várias rodadas, empate, saída no meio da partida, fechamento da aba, desafio a um jogador ocupado, queda do oponente e desafio simultâneo.

## Limitações

- Uma partida por vez para cada jogador
- A queda do oponente leva até 15 s para ser detectada (tempo do heartbeat do Pitiupi)
- Nome e IP do remetente vão dentro da mensagem, sem autenticação

## Autor

Samuel Crístian Amaral das Neves - Redes de Computadores
