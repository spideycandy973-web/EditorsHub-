package com.example.model

enum class AppLang(val code: String, val displayName: String) {
    ID("id", "Bahasa Indonesia"),
    EN("en", "English")
}

object OmsStrings {
    fun tr(lang: AppLang, id: String, en: String): String = if (lang == AppLang.ID) id else en

    fun navHome(lang: AppLang) = tr(lang, "Beranda", "Home")
    fun navTutorial(lang: AppLang) = tr(lang, "Tutorial", "Tutorials")
    fun navProjects(lang: AppLang) = tr(lang, "Proyek", "Projects")
    fun navTemplates(lang: AppLang) = tr(lang, "Templat", "Templates")
    fun heroTitle(lang: AppLang) = tr(lang, "Buat Animasi Gerak Luar Biasa", "Create Extraordinary Motion")
    fun heroSubtitle(lang: AppLang) = tr(
        lang,
        "Editor gerak, vektor Bezier, tracker optik, audio FFT, dan compositing 3D penuh.",
        "Full mobile motion graphics, Bezier vectors, optical tracking, FFT audio, and 3D compositing."
    )
    fun startNewProject(lang: AppLang) = tr(lang, "Mulai Proyek Baru", "Start New Project")
    fun quickNew(lang: AppLang) = tr(lang, "Proyek Baru", "New Project")
    fun quickNewSub(lang: AppLang) = tr(lang, "Buat kanvas kosong", "Blank composition")
    fun quickTut(lang: AppLang) = tr(lang, "Tutorial Cepat", "Quick Guides")
    fun quickTutSub(lang: AppLang) = tr(lang, "Pelajari fitur", "Learn workflows")
    fun quickTmpl(lang: AppLang) = tr(lang, "Templat Siap", "Ready Templates")
    fun quickTmplSub(lang: AppLang) = tr(lang, "Mulai instan", "Start instantly")
    fun quickImport(lang: AppLang) = tr(lang, "Impor Proyek", "Import Project")
    fun quickImportSub(lang: AppLang) = tr(lang, "File / Link Proyek", "File or Project Link")
    fun recentProjects(lang: AppLang) = tr(lang, "Proyek Terkini", "Recent Projects")
    fun seeAll(lang: AppLang) = tr(lang, "Lihat Semua →", "See All →")
    fun yourProjects(lang: AppLang) = tr(lang, "Proyek Anda", "Your Projects")
    fun noProjectsHint(lang: AppLang) = tr(
        lang,
        "Belum ada proyek yang dibuka. Tekan ＋ untuk membuat proyek pertama!",
        "No projects yet. Tap ＋ to create your first motion project!"
    )
    fun createProjectTitle(lang: AppLang) = tr(lang, "Proyek Baru", "Create Project")
    fun projectNameLabel(lang: AppLang) = tr(lang, "Nama Proyek", "Project Name")
    fun aspectRatioLabel(lang: AppLang) = tr(lang, "Rasio Aspek", "Aspect Ratio")
    fun resolutionLabel(lang: AppLang) = tr(lang, "Resolusi", "Resolution")
    fun fpsLabel(lang: AppLang) = tr(lang, "Frame Rate", "Frame Rate")
    fun durationLabel(lang: AppLang) = tr(lang, "Durasi (detik)", "Duration (sec)")
    fun backgroundLabel(lang: AppLang) = tr(lang, "Latar Belakang", "Background")
    fun createBtn(lang: AppLang) = tr(lang, "Buat Proyek", "Create Project")
    fun exportTitle(lang: AppLang) = tr(lang, "Ekspor & Bagikan", "Export & Share")
    fun importTitle(lang: AppLang) = tr(lang, "Impor Proyek (File / Link)", "Import Project (File / Link)")
}
