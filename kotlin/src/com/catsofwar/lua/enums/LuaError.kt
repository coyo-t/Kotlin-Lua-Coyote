package com.catsofwar.lua.enums

@JvmInline
value class LuaError(val luaEnum:Int)
{
	fun isOkay () = this == Ok

	inline fun check (lazyMessage:(error:LuaError)->Any)
	{
		if (this != Ok) lazyMessage(this)
	}

	inline fun checkThrowing (lazyMessage:(error:LuaError)->Any)
	{
		check(this == Ok) { lazyMessage(this) }
	}

	companion object
	{
		val Ok = LuaError(0)
		val Yield = LuaError(1)
		val RuntimeError = LuaError(2)
		val SyntaxError = LuaError(3)
		val MemoryError = LuaError(4)
		val Error = LuaError(5)
	}
}