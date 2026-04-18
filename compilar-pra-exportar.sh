#!/bin/bash

# Compila o Anything RPG e cria um arquivo ZIP portável com Java embutido

set -e

echo ""
echo "===================================="
echo "  COMPILADOR - Anything RPG"
echo "===================================="
echo ""

echo "⏳ Compilando e empacotando..."
./gradlew clean packageWindowsPortable

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Compilação concluída com sucesso!"
    echo ""
    
    echo "📦 Arquivo gerado:"
    ls -lh build/distributions/anything-*.zip 2>/dev/null || echo "Verifique build/distributions/"
    
    echo ""
    echo "Para usar em outro PC:"
    echo "  1. Extraia o ZIP"
    echo "  2. Execute o arquivo run-anything.cmd (Windows) or run-anything.ps1"
    echo "  3. Não é necessário instalar Java!"
    echo ""
else
    echo ""
    echo "✗ Erro na compilação!"
    echo ""
    exit 1
fi
