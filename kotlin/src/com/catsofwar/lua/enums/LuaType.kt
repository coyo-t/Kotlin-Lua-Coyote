package com.catsofwar.lua.enums

@JvmInline
value class LuaType internal constructor (val id:Int)
{
	val typeName: String get() {
		if (nametableSize < 0)
			throw IllegalStateException("LuaType nametable not initialized!")
		val ii = id + 1
		if (ii !in 0..nametableSize)
			throw IllegalArgumentException("bogus LuaType id $id")
		return nametable[ii]
	}

	companion object
	{
		val NONE = LuaType(-1)
		val NIL = LuaType(0)
		val BOOLEAN = LuaType(1)
		val LIGHTUSERDATA = LuaType(2)
		val NUMBER = LuaType(3)
		val STRING = LuaType(4)
		val TABLE = LuaType(5)
		val FUNCTION = LuaType(6)
		val USERDATA = LuaType(7)
		val THREAD = LuaType(8)

		internal var nametableSize = -1
		internal lateinit var nametable: Array<String>
	}
}