package com.catsofwar.lua.enums

enum class LuaBuiltinLibrary(val lname:String)
{
	COROUTINE("coroutine"),
	TABLE("table"),
	IO("io"),
	OS("os"),
	STRING("string"),
	UTF8("utf8"),
	MATH("math"),
	DEBUG("debug"),
	PACKAGE("package"),
	BASE(""),
}