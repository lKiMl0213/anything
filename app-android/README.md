# app-android

Base inicial para a futura interface visual Android/Compose.

Status atual:
- modulo ainda nao integrado ao build principal;
- estrutura criada para acelerar a proxima fase de UI.

Estrutura inicial:
- `src/main/kotlin/rpg/android/`
  - `navigation/`
  - `screens/`
  - `components/`
  - `theme/`

Objetivo:
- conectar essa camada ao `core` sem alterar regras de gameplay;
- manter a CLI (`app-cli`) como app funcional atual.
