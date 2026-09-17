

## v8.3.125 compile correction
- Fixed Kotlin receiver/parameter shadowing in `SettingsScreen.addSectionHeader()`: both TextView assignments now explicitly use `this.text`, resolving the CI errors at lines 235–236.
