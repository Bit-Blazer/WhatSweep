# WhatSweep

WhatSweep is an Android application that helps you organize and clean up your WhatsApp media files by identifying and categorizing notes, documents, and other media files.

## Features

- **Smart Classification**: Uses machine learning to automatically identify and classify WhatsApp notes and other media files
- **Media Management**: Scan, organize, and delete unwanted WhatsApp media files
- **Notes Identification**: Automatically identifies notes/documents from regular photos and media
- **PDF Support**: Handles PDF files with page-by-page preview capabilities
- **Modern UI**: Built with Jetpack Compose following Material 3 design principles
- **Dynamic Color Theming**: Adapts to your device's theme (supports light/dark mode)
- **Edge-to-Edge UI**: Full-screen immersive experience

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Media Handling**:
  - Image processing with Coil
  - PDF rendering with Android's PdfRenderer
- **Machine Learning**:
  - TensorFlow Lite for on-device media classification
  - Google ML Kit for custom image labeling
- **Navigation**: Jetpack Navigation Compose
- **Permissions**: Accompanist Permissions handling

## Requirements

- Android 5.0 (API level 21) or higher
- Storage permissions for accessing WhatsApp media files

## Building the Project

1. Clone the repository
2. Open the project in Android Studio Hedgehog (2023.3.1) or newer
3. Sync Gradle files
4. Build and run the application

## Project Structure

- **model**: Data classes for media file representation
- **repository**: Media scanning and processing logic
- **ui**: Jetpack Compose UI components and screens
- **util**: Utility classes for preferences and file operations
- **viewmodel**: Application logic and state management

## How It Works

WhatSweep scans your device for WhatsApp media folders and uses machine learning to identify which files are notes/documents versus other types of media. The app categorizes these files and allows you to select and delete unwanted files to free up storage space.

## Permissions

The application requires the following permissions:

- `READ_EXTERNAL_STORAGE` (for Android versions below 33)
- `MANAGE_EXTERNAL_STORAGE` (for Android 10+)

## License

MIT LICENSE
