# FocoLeitura — Android

Leitor RSVP audiovisual offline para Android, com importação de PDF e texto, narração nativa (TTS), configurações de foco, prévia dos documentos e efeitos visuais suaves sujeitos a consentimento.

## Organização

- `app/src/main/java/br/com/focoleitura/MainActivity.kt`: Activity Android, seletor de arquivos, PDF e TTS.
- `app/src/main/assets/`: interface e motor de leitura preservados (JavaScript/CSS/HTML).
- `app/src/main/AndroidManifest.xml`: configurações do app Android.
- `app/build.gradle.kts`: dependências e configuração do módulo Android.
- `build.gradle.kts` / `settings.gradle.kts`: configuração Gradle.
- `gradle/wrapper/`, `gradlew`, `gradlew.bat`: Gradle Wrapper 8.9.
- `.github/workflows/compilar.yml`: compilação de APK a partir do **código-fonte**.

O projeto é Kotlin + WebView com assets locais. Não foi migrado para Compose, para evitar perder funcionalidades existentes. As atualizações devem modificar os arquivos acima diretamente, sem ZIP intermediário e sem scripts de substituição.

## Compilar

No GitHub: **Actions → Compilar FocoLeitura Android → Run workflow**. Após concluir, baixar o artefato `FocoLeitura-APK-debug`, extrair `app-debug.apk` e instalar.

Em um ambiente com JDK 17 e Android SDK API 35:

```sh
./gradlew :app:assembleDebug
```

O resultado estará em `app/build/outputs/apk/debug/app-debug.apk`.

## Dados e atualizações

O aplicativo armazena documentos, configurações e progresso localmente (WebView/IndexedDB). Mantenha o mesmo `applicationId` (`br.com.focoleitura`) e utilize a **mesma chave de assinatura** para atualizações instaladas sobre outra versão. APKs de depuração gerados em máquinas diferentes podem ter chaves distintas; faça backup pelo aplicativo antes de reinstalar.

Não há servidor próprio ou envio automático dos documentos. O OCR de PDF digitalizado e a validação completa em aparelho físico continuam pendentes.

## Segurança visual

A apresentação utiliza transições suaves e opcionais: não oferece flashes estroboscópicos rápidos. O modo com efeitos exige confirmação por documento e todos os efeitos começam desativados.

## Manutenção

O histórico do Git preserva o ZIP legado e o antigo script de patch, que **não são mais necessários** na branch atual. Para novas alterações, edite os arquivos fonte e valide a execução do Actions.

## Duração das transições visuais

Em **Configurações → Estímulo visual dinâmico**, é possível ajustar o tempo da mudança de cor entre **0,1 e 10 segundos** (barra deslizante ou valor numérico). A preferência fica salva localmente, inclusive quando o modo de efeitos estiver desligado. O intervalo entre mudanças permanece independente e respeita um mínimo de 8 segundos; quando a duração é longa, o intervalo efetivo aumenta para que a transição termine. Abaixo de 1 segundo, apenas uma alteração discreta de cor do texto é permitida, sem mudança do fundo. O consentimento para os efeitos continua obrigatório a cada documento e a preferência do sistema por movimento reduzido desativa os efeitos.

## Trocar arquivo no Android

O botão **Trocar documento** no leitor (inclusive no Modo Foco) abre o seletor nativo Android, que lê PDFs e textos de armazenamento interno ou cartão SD usando a URI fornecida pelo sistema. O caminho do input HTML também usa esse seletor. Cancelar não troca a leitura; o progresso anterior é salvo antes da substituição visual e continua disponível na biblioteca. Não é necessário acesso amplo ao armazenamento.

## OCR local para PDF digitalizado

O leitor agora tenta extrair texto por página. Se uma página contiver somente uma imagem, ela é renderizada pelo Android e reconhecida pelo **ML Kit Text Recognition, modelo Latin incluído no APK**. Não usa Gemini nem envia o PDF para servidores, inclusive quando o documento vem do cartão SD. O processo é automático, pode levar algum tempo e não impõe um limite fixo de páginas, mas preserva os limites de 50 MB de arquivo e 4 milhões de caracteres extraídos. A qualidade do resultado depende da nitidez e do idioma; confirme a prévia antes de iniciar a leitura. O OCR funciona no aplicativo Android; a versão web continua com o extrator limitado.

## Sincronização do áudio com PPM

O valor escolhido no controle de PPM define o ritmo-alvo da leitura também ao ativar a voz. A taxa de TTS é calculada com uma referência nominal de 180 palavras/minuto em 1×, recebe um ajuste gradual conforme o tempo de cada trecho e é limitada à faixa suportada no app, de 0,5× a 4×. O controle **Ajuste fino da voz** funciona como multiplicador. A leitura só muda de grupo depois de a voz terminar e, se a fala for rápida demais, espera o restante do tempo. Não há garantia de atingir valores muito altos de PPM, pois cada sintetizador tem suas próprias limitações.

### Importação de PDFs longos (2.4.1)

Removido o limite arbitrário de 150 páginas: o OCR percorre também PDFs de 351 páginas ou mais, uma página de cada vez. Mostra progresso percentual e permite cancelar, sem substituir a leitura atual. Textos grandes são enviados à interface em blocos de até 16.384 caracteres, evitando uma chamada JavaScript gigante. O aparelho precisa de memória e espaço temporário suficientes; os limites de tamanho do arquivo e do texto extraído continuam valendo.
