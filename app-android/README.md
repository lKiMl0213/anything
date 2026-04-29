# app-android

Aplicativo Android em Jetpack Compose conectado ao core do jogo.

Status atual:
- modulo Android configurado (`com.android.application`);
- `MainActivity` real + `AndroidManifest`;
- navegacao por toque ligada ao `GameActionHandler`/`GamePresenter`;
- criacao de personagem por toque (raca/classe/atributos);
- atributos com `[-]/[+]` e aplicacao em lote;
- talentos, inventario, equipados, progresso, cidade e exploracao navegaveis por botao;
- combate semi-ATB basico por toque (`Atacar`, `Usar item`, `Fugir`).
