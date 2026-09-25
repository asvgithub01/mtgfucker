# Pruebas guiadas del scanner por reglas

Versión: `rules-modern-footer-3` · rama `codex/rules-scanner` · 23/09/2026.

## Orden de implementación

1. **Arte exclusivo:** implementado; Crecimiento gigante SOA #52 y Archimago emérito SPG #150 funcionan en vivo según el usuario.
2. **Pie moderno (edición + número + idioma):** implementado en este incremento. Pendiente validar con fotos reales de artes compartidos.
3. **Símbolo de edición:** siguiente bloque; no basta cuando dos sets reutilizan símbolo. No está activado aún en esta Activity.
4. **Reimpresiones/promos y marcas:** ampliar perfiles y distinguir pies heredados antes de resolver esos casos automáticamente.
5. **Antiguas y layouts especiales:** borde/copyright/detalles de diseño, caras y orientación.
6. **Acabado visual:** experimento con varios frames; de momento se usa el check Foil existente como preferencia explícita.

El orden no implica que un solo dato determine siempre una edición: las contradicciones y alternativas tienen prioridad sobre añadir una carta.

## Preparación

- Abrir el cuarto FAB → **Scanner por reglas · laboratorio**.
- Dejar marcado **Autoañadir por reglas**. El texto indica `nonfoil` o `foil`, heredado del check Foil de la sesión principal.
- Iluminar el pie, evitar reflejos y encuadrar las cuatro esquinas. No recortar el borde inferior.
- Usar la biblioteca de pruebas habitual. Cada lectura aceptada puede añadir una copia; revisar sesión y utilizar deshacer si es una prueba.
- No es necesario comprar cartas ni tener todas las de la lista: sirve cualquiera de los pares o cualquier otro caso equivalente.

## Primera tanda: mismos dibujos, distinta edición

Estos grupos comparten `illustrationId` en el asset **actual de la app**; no son una afirmación de cobertura global futura.

| Carta | Edición A | Edición B | Otra alternativa del mismo arte |
|---|---|---|---|
| **Podredumbre mental / Mind Rot** | M20 #108 | M21 #115 | M19 #109, KLD #93 |
| **Hada malhechora / Faerie Miscreant** | ORI #57 | M20 #58 | — |
| **Arremetida segura / Sure Strike** | M19 #161 | M21 #163 | BFZ #157 |

**Prueba principal:** si tienes dos ediciones, pasar **A → B → A**. Con arte fuerte y pie correctamente leído debe guardar el UUID de cada edición, sin arrastrar la elección anterior. La evidencia del detalle debe indicar `AUTO_STRUCTURED_FOOTER` / `STRUCTURED_FOOTER`.

Si solo tienes una edición, comprobar set, número e idioma guardados. No hace falta disponer físicamente de ambas para comenzar.

**Qué exige esta versión:** tupla completa coincidente en al menos dos familias de recorte OCR de la misma foto, arte fuerte con margen, idioma sin conflicto y un UUID compatible. Es una comprobación de consistencia entre recortes, **no dos observaciones físicas independientes** ni una confianza calibrada.

## Segunda tanda: controles negativos

| Prueba | Resultado esperado |
|---|---|
| En una carta del cuadro anterior, tapar solo código/número del pie sin tapar arte ni título | No escoger una edición por ser top1; revisión manual. No usar para esta prueba los dos artes exclusivos, porque su ruta no necesita el pie. |
| Pie con reflejo, fuera de encuadre o muy borroso | Si falta corroboración o hay lecturas incompatibles, revisión; no rellenar datos inventados. |
| Arte compartido con posible The List, Mystery Booster o promo | Revisión, motivo `RETAINED_FOOTER_POSSIBLE`. Aún no se detecta visualmente su marca diferenciadora. |
| Grupo con un producto cuyo perfil de set no está soportado/conocido | Revisión, `UNSUPPORTED_SET_PROFILE`; que el pie parezca legible no permite borrar esa alternativa. |
| Desmarcar Autoañadir por reglas | Ningún guardado automático, incluso con una lectura correcta. |
| Cancelar un selector o confirmación | No cambiar cantidades ni edición. |

El auto por pie se limita de momento a grupos cuyos sets tienen tipo conocido `core`, `expansion`, `masters` o `draft_innovation`. Mystery Booster se excluye expresamente aunque su tipo sea de draft. No se pretende resolver todavía todo Commander, Secret Lair, promos o productos especiales.

## Tercera tanda: regresión y propiedades físicas

- Repetir **Crecimiento gigante SOA #52** y **Archimago emérito SPG #150**: deben seguir funcionando por arte exclusivo, sin depender de leer perfectamente el número.
- Si tienes ES/PT del mismo nombre, comprobar **idioma impreso**, no el idioma del nombre canónico. No debe usar inglés por defecto si no sabe el idioma.
- Probar foil/no foil cambiando el check existente: debe guardar el acabado indicado y disponible en catálogo. Esto **no comprueba detección visual de foil**, que sigue pendiente.
- Repetir una carta seguida: debe abrir el panel de copias existente, sin duplicaciones descontroladas. Corregir edición y deshacer deben mantener la sesión coherente.

## Qué comunicar si falla

1. Nombre, código, número e idioma de la carta física.
2. Si autoañadió una impresión incorrecta o si se quedó en revisión.
3. Estado y motivo del panel (`FOOTER_NEEDS_CORROBORATION`, `FOOTER_NOT_IN_ART_CATALOGUE`, `ART_MARGIN`, etc.).
4. Mantener la captura reciente: se conservan las últimas 30 en el dispositivo. Las fotos no se suben a Git.

## Validación técnica de esta versión

- 314 pruebas JVM sin fallos, incluidas reglas negativas y grupos reales del catálogo.
- 19 pruebas Android correctas en Sony: OCR de pie generado para pruebas, selección de ediciones de Mind Rot con catálogo/Room reales y tuplas de prueba, replay de las dos fotos reales SOA/SPG y regresiones de UI/copias/evidencias.
- Las pruebas de Mind Rot **no son fotos de esas cartas**: falta validación de la lectura óptica en vivo con las cartas de la primera tanda.
- APK actualizada con `install -r`, sin borrar datos; SHA256 `9a0303c2dca6a15ea712428511204841a508f0e6eef7e19e2bd1aea59de09451`.

## Tanda OCR quality 4 (prioritaria)

1. **Rules:** Hada malhechora ORI57,10lecturas con luz/enfoque normales; anotar aciertos, revisión y tiempo. Repetir SOA52/SPG150 como regresión. La captura borrosa antigua sigue correctamente sin corroboración aunque ahora leeORI57.
2. **OCR original:** Ajustes → «OCR experimental: CLAHE y mayor resolución» (OFF por defecto). Mismas blancas antiguas,10lecturasOFF y10ON; alternar orden para reducir efecto de calentamiento. Confirmar nombre/idioma, tiempo, encuadre y fluidez; desactivar para volver a ruta anterior. No cambia la lógica de selección/guardado.
3. **Negativos:** dedo sobre pie, dedo sobre título, carta parcial, desenfoque y pie tapado. Anotar por separado; las métricas de nitidez aún NO detectan oclusiones. Nunca inferirORI por arte si falta evidencia de edición.
4. La resolución es solicitada, no garantizada; comprobar en dispositivo el tamaño efectivo y que el mayor detalle compensa el coste. Replayactual315JVM/9Androidcorrectos; pendientes estas pruebas físicas.

## Tanda perfiles5

- OCRoriginal: checkexperimental ahora enpantalla deescaneo, debe conservarONdelusuario;probarON/OFFyencuadreenpantallapequeña.
- Rules: mover sliderzoomconcartacompleta; no debe capturar mientrasarrastramos ni reutilizar bordes anteriores. Probar1x/1.5x/2x según rango delteléfono. Zoom digital no recupera detalle óptico; evitar cortar bordes. Cambiolentesmanual no implementado.
- Copias: abrirselector de una carta reciénreconocida ycompararconsesión; edicionesyaenRoom debenaparecerantesprecios. Primera carta sincatálogo local aúnpuede necesitarred. VerificarselecciónUUID/idioma/acabado/cantidad.
- Antiguas: ArcboundWorker/Frogmite/Thoughtcast/VaultSkirge; PARTIALsignificaaño+número leídos, NO ediciónexacta resuelta. Pies sin fracción/año legibles siguenUNREADABLE. No exigirautoañadido hasta implementarperfilantiguo+sím­bolo.

## Tanda reintentos6

1. Rules: fijarzoom(p.ej.1.7x), escanear yvolveracámara;cerraryabrirscanner. Debe conservarratio, no1x. Otros scannersCameraX guardan suajuste porseparado.
2. Carta moderna conpie poco legible: mantenerla quieta mientrasapareceintento1/3,2/3,3/3. Debeañadirencuantotenga evidencia suficiente, sin pedirconfirmaciónalprimerfallorecuperable.
3. Trescapturasfallidas: una revisiónfinal,nobucle. AutoOFF/manual: revisiónsinráfaga. PLST/promos/perfiles no soportados siguenrevisión inmediata.
4. Cambiardecartaentreintentos no debecombinarpies/nombres/idiomas;soloresultadodecadafoto puedeautoañadir. Salir/pausar yretomarreinicialacuentasin guardadosenbackground.


## Pie histórico por campos — rules-historical-fields-7

Este paso recupera evidencia, NO elige aún una edición antigua por año. No cambia cámara, pasadas OCR, hashes ni autoañadido.

1. Abrir el cuarto FAB (reglas), desactivar su autoañadido para inspeccionar sin sumar copias.
2. **Pacifismo antiguo / Robert Bliss**: 3 capturas con luz difusa. En revisión buscar `Año/copyright: 1996`; artista puede mostrar variantes OCR/CONFLICT. Número sin lectura debe seguir UNREADABLE, nunca inventado.
3. **Atraer al fantasma / Cliff Childs**: 3 capturas; esperar año2013 y artista. No exigir número si no se lee. Ninguna edición debe declararse definitiva solo por estos campos.
4. **Shaman en-Kor**: opcional, comprobar1998. El artista puede seguir ilegible.
5. Cubrir el pie antes de capturar: no deben recuperarse campos de una carta anterior. No es una prueba de detección automática de dedos.
6. Reactivar auto para regresión con Hada ORI, Crecimiento gigante SOA y Archimago emérito. Estas pruebas sí pueden sumar copias; revisar sesión. Comprobar también zoom tras reapertura.
7. Informar carta/edición real, año visible, valores mostrados, tiempo aproximado y si fallan todas o alguna de las3capturas. Se conservan30intentos privados; no escanear muchas más antes de revisarlos.

Siguiente incremento: ROI horizontal histórico (copyright/número), después cruce conservador con catálogo y símbolo. Un copyright puede ser rango o compartirse entre ediciones: no es fecha de lanzamiento garantizada.


## Compatibilidad de formatos — rules-historical-formats-8

Autoañadido OFF; tres capturas por carta con pie completo:
- Spin into Myth/Espiramitológico: rango1993–2007 y2007 compatibles (READ), artistaDavidDay ahora admisible a derecha, fracción60/180 cuando legible.
- Sarcomite Myr: rango1993–2007/2007; artistaMichaelBruinsma, número56/180 cuando legible.
- Titanic Bulvox:1993–2003/2003 compatibles;129/143 si legible.
- Flickering Spirit:2006/rango1993–2006;17/301. Si otra pasada dice17/501 debe mantenerCONFLICT: no escoger por mayoría.
- Pacifismo/Atraer: regresión1996/2013.
No probar aún resolución de cartas sin año. No cambiaautoañadido ni perfiles de captura; pie global puede seguirPARTIAL aunque añoREAD, pues edición no está resuelta. Las variantesOCR del artista continúan visibles. ©real se separa, O/Cambiguos no se borran ciegamente.


## Tabla de lectura en revisión

Con autoOFF escanear una carta: justo debajo del título de resultado debe aparecer Dato/Valor/Estado, sustituyendo el párrafo Arte fuerte y los antiguos bloques. Año compatible aparece✅Leído; número contradictorio⚠️Conflicto; sin dato❌Sin lectura. Arte es candidato⚠️Parcial, no impresiónconfirmada. Comprobar scroll hacia búsqueda/Evidencias con fuentegrande/pantallapequeña; JSONcompleto sigue accesible. La tabla muestra la captura actual, no historial, y no altera reglas de guardado.


## Arte y título — rules-art-title-9

AutoOFF,3capturasporcarta:
- TitanicBulvox: arte✅Fuerte si p<=10,d<=16 y margenp>=4 frente aotrasilustraciones. No significaediciónresuelta. Si distancias/margenempeoran, avisos⚠Débil/⚠Sinmargen soncorrectos.
- FarrelitePriest: comprobarNombreOCRresuelto incluso lecturasFarrelítePricst/Prist. Fallbackdiccionariocompletorecuperó3/3capturasguardadas; mantienetolerancias ypuedeañadirtiempo soloenlecturasdifíciles.
- BenalishHero: siguecasopendiente; si texto noresuelvenombre mostrarvariantesOCR+⚠Sinresolver envezde❌Sinlectura. No forzarnombreporhash.
- SarcomiteMyr/SpinintoMyth: candidatomaloodistanciasaltas debenavisar, noaparecerverdeporcoincidirsoloelnombreOCR.
- RegresiónSOA/SPG/Hada y pieshistóricos de testsanteriores. Textocrudo/arte/timings quedanJSON; OCRoriginalmantienerutaprevia.
Siguiente: crucefooterconcatálogo cuando camposfiables;noañadidaautoediciónhistóricaenesteincremento.


## Cruce histórico explicable — rules-historical-compare-10

AutoOFF. Bajo la tabla aparecerá Ediciones candidatas · cruce del pie, solo para pies históricos con evidencia y sin tupla moderna.
1. TitanicBulvox:3capturas. Si lee129/143 y2003, SCG#129 debe tener coincidencia número y año lanzamiento; si nolee fracción, númeroSINCONTRASTAR, no inventado.
2. FlickeringSpirit:3capturas. TSP#17/año2006 deben coincidir cuando lee17/301. Si17/301vs17/501 o17/901vs17/30, conservarCONFLICT sin escoger el dato que mejor encaja.
3. Otrasimpresiones/reimpresiones deben seguir visibles o enVerEvidencias, aunqueaño/númerodifieran. Se ordenan primero coincidencias; no seeliminan ni seautoañaden.
4. SiOCRreconoce nombrepero shortlistartefalló, puede indicarNO_INDEX_CANDIDATES. No significa que carta/ediciónnoexista; usarBuscar/Revisar. Soncandidatos delíndicevisualparcial,no todoelcatálogo.
5. Año mostrado en candidatos esLANZAMIENTOcontrastado conañoOCR, soloindicio; artistaytotalimpresono contrastadosporqueíndicecarecededatos. No exigirartistaverificado.
6. RegresiónHada/SOA/SPG: siguenrutaactual; no deben mostrarcrucehistórico sihaytuplamoderna. Esta sección no modificaautoañadidoexistente.


## Artista de referencia — rules-artist-reference-11

AutoOFF,3capturasporcarta. En Edicionescandidatas ahora apareceArtista catálogo:
- TitanicBulvox/SCG:WayneEngland. SiOCRlegibleysinalternativas contradictorias,✅Coincide;faltanúmeropuede seguirSincontrastar.
- FlickeringSpirit/TSP:AlexHorley-Orlandelli. Lectura únicaigual→✅Coincide;si salenAlexI/zAlexHorley-Orlandelli juntoalnombrecorrecto,⚠Conflicto: no elegir el dato queencaja por tener catálogo.
- SpinintoMyth/FUT:DavidDay; opcionalregresión de artista a derecha.
- Taparartistapara controlnegativo:catálogopuedemostrarreferencia,peroestadoSincontrastar;no debeinventarlecturaOCRdesdecatálogo.
- Sin cambioautoañadido,cámara,OCRoriginal,hashes. CatálogoreferenciaexactaUUID+ilustración, no nombre de carta solamente. Falta artista en22paresdelíndice;eso no significa errorOCR.
Siguiente: metadatos/perfiles validados para denominador ycopyrightimpreso antes deconfirmarimpresión automáticamente. Lasreglasactuales aún sonrevisión,noautoelegirhistóricasporser1candidata.

## v12 — título localizado, idioma y edición

1. Autoañadir OFF: tres capturas de **Espírito Flutuante**. Esperado «Idioma título: pt: Espírito Flutuante» y PT final incluso si el texto de reglas queda por debajo de 0,80. Debe seguir TSP #17 como candidato, no edición confirmada solo por idioma.
2. **Bôifalo Titânico**: título PT más reglas PT; artista independiente. Mantener carta completa, sin reflejo en el borde inferior.
3. Si se dispone, **Elfos de Llanowar** en ES/PT: título compartido debe mostrar ambas lenguas; resolver solo con pie/reglas. Tapar esas regiones: no inventar ES ni EN.
4. Seleccionar edición manualmente: PT debe aparecer preseleccionado, editable. Confirmar una copia y verificar idioma PT en sesión/Biblio después de reabrir. Cambiar deliberadamente el selector para comprobar que se respeta la decisión manual. No modifica cartas ya guardadas.
5. Regresión modernas: Crecimiento gigante y Archimago siguen las barreras de autoañadido; título/pie contradictorios deben bloquear, no decidir por mayoría.
6. «Idiomas indexados» en histórico indica compatibilidad, no exhaustividad. Falta PT en catálogo parcial => sin evaluar, nunca «no existe».

Capturas v11 revisadas:30 en `build/language-v12-review` (privadas).14 Flickering con identidad única:14 nombres legibles, solo5idiomas PT con regla anterior. Hay recorte desplazado (1790267086346) y reflejo en pie (1790267360076); resolución permanece sin cambios. Instrumental opt-in `RulesTitleLanguageDeviceTest` necesita esos30JSON en `no_backup/rules_replay/language_v12`; no escribe colección.

## v13 — comprobación manual tras instalación Sony

- AutoOFF,3lecturasEspíritoFlutuante y3BôifaloTitânico. «Idioma leído: PT» ahora aparece encima de tabla.
- Elegir edición+número; esperar consulta de idiomas. Solo opciones del catálogo para impresión;PTpreseleccionado si figura. Si falla red, aviso de lista manual sin filtrar, no confirmación de idiomasposibles.
- Confirmar1copia, comprobarPT en sesión yBiblio tras reabrir. Cancelar carga/selector no debe añadir ni abrir un diálogo tardío.
- Si PT no aparece en una lectura, conservarla para diagnóstico;historial guarda últimas30, evitar muchas capturas antes de revisarlo.

## v14 — separar número y total del footer

Con autoañadirOFF,3capturasdeEspíritoFlutuante(TSP17/301),BôifaloTitânico(SCG129/143),opcionalSpinintoMyth(FUT60/180). Tablaahoramuestra «Número antiguo» y «Total impreso» separados. Comprobarquecoincidennúmero,total,año,artista,idioma.

- Si varía solo total entrepasadas, número puede✅leído/coincidente aunque total⚠conflicto. No representa ediciónconfirmada.
- Si cambianumerador, debe seguirconflicto aunque uno coincida con catálogo; no elegirlo por mayoría.
- Un pie tapado/sin fracción sigue sinlectura: no se rellena desde catálogo.
- PT yselector porimpresión deben mantenerse; cámara/OCR original/autoañadido no cambian.
- Replayhistórico1790257475776 conserva17/901y17/30 conflictivos pero17 ahoraMATCH conTSP17; prueba instrumentalassertaambos estados.

## v15 — copyright con TM y glifo C mal reconocido

Priorizar **Twilight Shepherd**:2–3capturas, autoOFF. Esperado copyright1993–2009 yartistaJasonChan cuando son legibles; no asumir que el número11/62 se recupera si OCRpierdebarra. Dejar completo bordeinferior dentrodelencuadre: algunasfotosanteriores terminan sobreelcopyright.

Comparar1Titanic y1Flickering para regresión. ZhalfirinCommander siguecasodifícil de nitidez/contraste; fotografiar completoysinreflejo para siguienteA/B. No se aumenta resolución ni se cambia OCR original en v15. Conservar capturasfallidas antes de superarventana30.

## v16 — artistas por diccionario

1. Scanner por reglas, autoañadido desactivado; Twilight Shepherd tres capturas con pie completo.
2. Esperar Jason Chan. Si OCR introduce un error menor y otra pasada lo lee exacto, tabla muestra «Artista OCR → normalizado» con ambos valores. Que la fila no aparezca con OCR exacto es correcto.
3. Copyright debe seguir1993–2009; número/total pueden seguir ilegibles: esta entrega no inventa11/62 desde1162.
4. Titanic Bulvox (Wayne England) y otra carta de artista diferente: comprobar que no se reutiliza Jason Chan entre cartas. Un conflicto real o error no corroborado debe permanecer visible.
5. Registrar3capturas por carta; no esperar mejora grande del tiempo total, todavía ejecutamos las pasadasOCR existentes.

Automáticas:9testsJVM de diccionario (raw, corroboración, ambigüedad, colisiones, exactos distintos, idempotencia, ausencia). ArtistDictionaryDeviceTest reproduce3JSON físicosv15 con vocabulario realglobalPFR1, sin guardar colección. Fixturesprivados no_backup/rules_replay/artist_v16; sin fixture el test falla, no se omite.

## v17 — captura parcial y recuperación live

1. Scanner por reglas, autoOFF. Carta entera centrada: tres Twilight yTitanic deben analizar normalmente.
2. Reproducir carta desplazada abajo con mesa encima ypie fuera. Si detecta ese patrón, mensaje «Comprueba que la carta completa esté dentro del marco. Reintentando…», cámara sigue abierta yno hay OCR/popup en ese intento.
3. Recentrar carta: debe continuar sin cerrar scanner. No se garantiza detección de todas las posiciones parciales.
4. Si mantiene encuadre sospechoso, tras2rechazos se permite análisis normal para no bloquear layouts válidos; no esperar rechazo infinito. Corrección manual yotros scannersintactos.
5. Recolectar logcat además de rules_scan: los reintentos ligeros no generan metadatosOCR. Tiempo de análisis de carta válida no debe confundirse con ahorro de OCR evitado.

Tests: RulesCaptureGateTest límites/NaN/failopen/blank/reseteo/cota; RulesCaptureGateDeviceTest fotos guardadas ycoste medición. Corpus privado no_backup/rules_replay/capture_v17, expectedsolo2parciales conocidas; no evalúa universalmente todaMagic.

## v18 — comparación de bordes real

AutoañadidoOFF. Primero casillaBordesfijosdesmarcada:3Twilight+3Titanic,cartaentera enpreview. Luego marcarBordesfijos:primeracapturaestableabreajuste;4esquinasenbordeexterior yLeer. Mantenerposicióncámara/cartaspara3+3lecturas. Cambiodezoom obliga recalibrar. Desmarcar/marcar trasvolveracámara recalibra. Modo persiste,referencia no sobrevivecerrarActivity. Cámaraestática/sinmoverreferenciaescondiciónfijo,no prometerseguimiento.

JSONboundaryMode distingueAUTO_OUTER_EXPERIMENT/USER_FIXED_SESSION; original.jpgpermitecompararquadconbordefísico. Manualstillpuedecareceroriginal,sinnegarlectura. Capturasal cambiarcarta no debenautoañadirse (autoOFF). Testssintéticosdetectorygettercopiapuntos;UIflagvisibleypresetvacíoalabrir. Física A/Brequiereusuario.
