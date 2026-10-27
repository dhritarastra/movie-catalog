package com.moviecatalog.gateway.person

/** `PersonInput` as received; the catalog validates and normalises it. */
data class PersonInput(val name: String, val bio: String?, val birthDate: String?)
