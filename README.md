# WhatSweep

WhatSweep is a modern Android application that helps you organize and clean up your WhatsApp media files by intelligently identifying and categorizing notes, documents, and other media files. Built with cutting-edge Android technologies and following Material 3 design principles.

## ✨ Features

- **🤖 Smart Classification**: Uses machine learning to automatically identify and classify WhatsApp notes and other media files
- **📁 Media Management**: Scan, organize, and delete unwanted WhatsApp media files with batch operations
- **📝 Notes Identification**: Automatically identifies notes/documents from regular photos and media
- **📄 PDF Support**: Handles PDF files with page-by-page preview capabilities
- **🎨 Modern UI**: Built with Jetpack Compose following Material 3 design principles
- **🌈 Dynamic Color Theming**: Adapts to your device's theme with full Material You support
- **📱 Edge-to-Edge UI**: Full-screen immersive experience with proper insets handling
- **⚡ Real-time Progress**: Live progress tracking during media scanning operations
- **🔧 Advanced Settings**: Customizable scan behavior and app preferences
- **🛡️ Robust Error Handling**: Comprehensive error management with user-friendly feedback
- **♿ Accessibility**: Full accessibility support with proper content descriptions

## 🛠️ Technology Stack

- **Language**: Kotlin with full null safety
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow
- **State Management**:
  - Kotlin StateFlow and MutableStateFlow
  - Compose state hoisting patterns
  - Comprehensive error state management
- **Media Handling**:
  - Image processing with Coil for efficient loading
  - PDF rendering with Android's PdfRenderer
  - Custom media file scanning with progress tracking
- **Machine Learning**:
  - TensorFlow Lite for on-device media classification
  - Google ML Kit for custom image labeling
- **Navigation**: Jetpack Navigation Compose with type-safe navigation
- **Permissions**: Modern permission handling with proper UX flows
- **Build System**: Gradle with Kotlin DSL
- **CI/CD**: GitHub Actions for automated builds and releases

## 📋 Requirements

- **Minimum SDK**: Android 5.0 (API level 21)
- **Target SDK**: Android 14 (API level 34)
- **Recommended**: Android 8.0+ for optimal performance
- **Storage permissions** for accessing WhatsApp media files
- **Device storage** space for media processing

## 🚀 Building the Project

### Prerequisites

- Android Studio Hedgehog (2023.3.1) or newer
- JDK 17 or higher
- Android SDK with API level 34

### Steps

1. Clone the repository:

   ```bash
   git clone https://github.com/Bit-Blazer/WhatSweep.git
   cd WhatSweep
   ```

2. Open the project in Android Studio

3. Sync Gradle files (Android Studio will prompt automatically)

4. Build and run:

   ```bash
   ./gradlew assembleDebug
   # or for release build
   ./gradlew assembleRelease
   ```

### CI/CD Pipeline

The project includes automated GitHub Actions workflows for:

- **Continuous Integration**: Automated builds on pull requests
- **Release Automation**: Automatic APK building and GitHub releases on version tags
- **Code Quality**: Automated testing and code analysis

## 🏗️ Project Architecture

The project follows a clean MVVM architecture with clear separation of concerns:

```tree
app/
├── src/main/java/com/bitblazer/whatsweep/
│   ├── model/           # Data classes and models
│   │   ├── MediaFile.kt # Core media file representation
│   │   └── ScanState.kt # State management models
│   ├── repository/      # Data layer and business logic
│   │   └── MediaScanner.kt # Media scanning and processing
│   ├── ui/             # User interface layer
│   │   ├── components/ # Reusable UI components
│   │   ├── screens/    # Application screens
│   │   │   ├── ResultsScreen.kt
│   │   │   └── SettingsScreen.kt
│   │   └── theme/      # Material 3 theming
│   ├── util/           # Utility classes
│   │   └── PreferencesManager.kt
│   └── viewmodel/      # State management
│       └── MainViewModel.kt
└── build.gradle.kts    # Build configuration
```

### Key Components

- **📱 UI Layer**: Modern Jetpack Compose screens with Material 3 design
- **🔄 State Management**: Reactive StateFlow-based MVVM architecture
- **💾 Repository**: Clean data access with proper error handling
- **🎯 ViewModels**: Centralized state management with lifecycle awareness
- **🎨 Theming**: Comprehensive Material 3 theming with dynamic colors

## 🔄 How It Works

WhatSweep provides an intelligent and user-friendly approach to WhatsApp media management:

1. **📂 Smart Scanning**: Automatically locates WhatsApp media folders across your device
2. **🤖 AI Classification**: Uses on-device machine learning to distinguish notes/documents from photos and other media
3. **📊 Real-time Progress**: Shows live progress with file counts and current processing status
4. **📋 Categorized Results**: Organizes files into clear categories (Notes, Other Media) for easy review
5. **✅ Batch Operations**: Select individual files or use "Select All" for efficient bulk operations
6. **🗑️ Safe Deletion**: Removes unwanted files while preserving important content
7. **⚙️ Customizable Settings**: Configure scanning behavior and app preferences to suit your needs

### Advanced Features

- **Progressive Loading**: Handles large media collections efficiently
- **Error Recovery**: Robust error handling with user-friendly feedback
- **Accessibility Support**: Full screen reader and navigation support
- **Theme Integration**: Seamlessly adapts to your device's Material You theme

## 🛡️ Permissions

The application requires the following permissions for optimal functionality:

### Required Permissions

- **Storage Access**: For reading WhatsApp media files
  - `READ_EXTERNAL_STORAGE` (Android < 13)
  - `READ_MEDIA_IMAGES` (Android 13+)
  - `MANAGE_EXTERNAL_STORAGE` (Android 10+ for comprehensive access)

### Permission Handling

- Modern permission request flows with clear explanations
- Graceful degradation when permissions are denied
- Settings deep-linking for easy permission management

## 🚀 Recent Improvements

### Code Quality & Architecture

- ✅ **Complete Material 3 Migration**: Updated all UI components to Material 3 design system
- ✅ **Enhanced Error Handling**: Comprehensive error state management throughout the app
- ✅ **State Management Refactoring**: Improved StateFlow usage and reactive programming patterns
- ✅ **Build System Optimization**: Updated Gradle configuration and dependencies
- ✅ **CI/CD Pipeline**: Automated builds and releases via GitHub Actions

### User Experience

- 🎨 **Dynamic Theming**: Full Material You support with dynamic color extraction
- 📱 **Edge-to-Edge Design**: Modern immersive UI with proper inset handling
- ♿ **Accessibility Improvements**: Enhanced screen reader support and navigation
- ⚡ **Performance Optimization**: Faster media scanning and UI responsiveness

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Guidelines

- Follow Kotlin coding conventions
- Use Jetpack Compose best practices
- Maintain Material 3 design consistency
- Include proper error handling
- Add accessibility support for new features

## 📄 License

MIT LICENSE
