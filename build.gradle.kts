val versionFile = layout.projectDirectory.file("VERSION").asFile
val versionLines = versionFile.readLines(Charsets.UTF_8)
if (versionLines.size != 1 || !Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matches(versionLines.single())) {
    throw GradleException("VERSION must contain exactly one SemVer core value")
}
val releaseVersion = versionLines.single()
extra["releaseVersion"] = releaseVersion
allprojects {
    version = releaseVersion
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
