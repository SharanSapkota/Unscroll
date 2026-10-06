package com.unscroll.app.domain

import javax.inject.Qualifier

/** A CoroutineScope that lives as long as the process, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
