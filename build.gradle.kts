plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

allprojects {
    group = "dev.isaacudy.udeploy"
    version = rootProject.libs.versions.udeployVersionName.get()
}
