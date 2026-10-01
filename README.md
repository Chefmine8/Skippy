# Skippy 🦘

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=flat&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material_3-7C4DFF?style=flat)](https://m3.material.io/)
[![Security](https://img.shields.io/badge/Security-AES--256--GCM_Keystore-00897B?style=flat)](https://developer.android.com/training/articles/keystore)

> **L'assistant d'assiduité intelligent pour étudiants.**  
> Synchronisez votre planning en direct, suivez votre quota d'absences autorisées matière par matière (règle des 60 %) et identifiez les cours que vous pouvez sécher en toute sécurité.

---

## ✨ Fonctionnalités

- **🔄 Synchronisation du planning :** Récupération automatique de votre emploi du temps avec support multi-groupes (promotion, classe, groupes de TD/TP).
- **📊 Suivi d'assiduité précis :** Décompte automatisé par matière et type d'activité (CM, TD, TP), jauge de quota consommé et alertes avant d'atteindre le seuil critique.
- **🧠 Suggestions « Smart-Skip » :** Détection des cours matinaux isolés, calcul de la marge de sécurité et priorisation personnalisée (*Important*, *Normal*, *À sécher en premier*).
- **🔔 Notifications interactives :** Validez votre présence (*Présent*, *Absent*, *Justifié*) directement depuis votre volet de notifications à la fin de chaque séance.
- **🔐 Données 100 % locales :** Aucun serveur externe ni télémétrie. Identifiants et planning restent chiffrés sur votre appareil (**Android Keystore / AES-256-GCM**).

---

## 📱 Les Écrans

- **📅 Semaine :** Navigation fluide dans votre emploi du temps, code couleur distinctif et badges clairs (*Séchage conseillé*, *Peut être séchée*, *Mieux vaut y aller*, *Important*).
- **📊 Matières :** Vue d'ensemble de vos matières, taux d'assiduité, consommation du quota et personnalisation des priorités.
- **📝 Séances :** Historique complet des cours avec filtres (*À renseigner*, *Passées*, *À venir*).
- **⚙️ Réglages :** Connexion au compte étudiant, choix des groupes et configuration des seuils (taux cible, réserve de sécurité).

---

## 🧮 Règle des 60 % & Calcul d'assiduité

1. **Clôture au premier examen :** Le quota d'absences est calculé jusqu'au premier examen de chaque matière. Les rattrapages ultérieurs ne faussent pas votre budget.
2. **Budget d'absences fixe :** Avec une obligation de 60 % de présence, vous disposez d'un budget maximal de 40 % d'absences sur le total des cours planifiés.
3. **Marge en direct :** Vos absences confirmées déduisent immédiatement des crédits. Les séances non encore renseignées ne pénalisent pas votre marge.
4. **Réserve de sécurité :** Définissez un nombre de cours de secours intouchables pour faire face aux imprévus de fin de semestre.

---

## 🚀 Compilation & Installation

### Prérequis
- **JDK :** Java 17 ou 21 (OpenJDK recommandé)
- **Android SDK :** Android 8.0+ (API 26+)

### Commandes

```bash
# Lancer les tests unitaires
./gradlew test

# Compiler l'APK de débogage
./gradlew assembleDebug

# Installer sur un appareil ou émulateur connecté
./gradlew installDebug
```

L'APK généré est disponible dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## ❓ FAQ

<details>
<summary><b>Les notifications post-cours ne s'affichent pas</b></summary>
Certains constructeurs (Samsung, Xiaomi, OnePlus) brident les processus d'arrière-plan. Rendez-vous dans <i>Paramètres > Applications > Skippy > Batterie</i> et choisissez <b>Non restreinte</b>. Vérifiez également que l'autorisation de notifications est accordée.
</details>

<details>
<summary><b>Mes données sont-elles partagées ?</b></summary>
Non. Skippy fonctionne exclusivement en local sur votre téléphone sans serveur intermédiaire.
</details>

---

<p align="center">
  <sub>Développé pour simplifier le suivi d'assiduité et préserver votre sommeil en toute sérénité. 🦘</sub>
</p>
