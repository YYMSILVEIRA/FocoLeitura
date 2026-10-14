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
