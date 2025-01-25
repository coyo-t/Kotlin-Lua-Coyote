package com.catsofwar.lua

import com.catsofwar.lua.enums.LuaType
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.foreign.ValueLayout.*
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles



internal class LuaDll private constructor()
{

	companion object
	{
		internal val LUA_STATE = ADDRESS.withName("L")

		private var initialized = false
		internal var initCallback: (()->Unit)? = null


		fun init()
		{
			if (initialized)
				return

			when (val cb = initCallback)
			{
				null ->
					throw IllegalStateException("No callback given to initialize lua DLL stuff!! :[")
				else -> try {
					cb()
				}
				catch (e: Throwable)
				{
					throw RuntimeException("Something went wrong when init-ing lua dlls!", e)
				}
			}

			initialized = true

		}

		fun createDllInst (): LuaDll
		{
			checkNotNull(initCallback) { "OW. NO CALLBACK. this caused silent crashes before!!" }
			init()
			return LuaDll().apply {
				Arena.ofConfined().use { f ->
					val sza = f.allocate(JAVA_LONG)
					val longesta = f.allocate(JAVA_LONG)
					var nametable = (luaL_gettypenametable(sza, longesta) as MemorySegment)
					val sz = sza[JAVA_LONG, 0]
					nametable = nametable.reinterpret(ADDRESS.byteSize() * sz)
					val longest = longesta[JAVA_LONG, 0]
					LuaType.nametable = Array(sz.toInt()) {
						val addr = nametable.getAtIndex(ADDRESS, it.toLong()).reinterpret(longest)
						String(
							(0..<longest)
							.map { addr[JAVA_BYTE, it] }
							.takeWhile { it.toInt() != 0 }
							.toByteArray()
						)
					}
					LuaType.nametableSize = sz.toInt()
				}
			}
		}

		internal val invokeDesc = FunctionDescriptor.of(
			JAVA_INT,
			ADDRESS,
		)

		internal val invokeHandle = MethodHandles.lookup().findVirtual(
			LuaCFunction::class.java,
			"invoke",
			invokeDesc.toMethodType()
		)

	}

	//#region lua.h

	//#region State manipulation

	val luaL_newstate by method(LUA_STATE)
	val lua_close by voidMethod(LUA_STATE)

	val lua_newthread by method(LUA_STATE, LUA_STATE)

	//#endregion

	//#region basic stack manipulation

	val lua_absindex by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Index")
	)

	val lua_gettop by method(
		JAVA_INT,
		LUA_STATE,
	)

	val lua_settop by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index")
	)

	val lua_pushvalue by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index")
	)

	val lua_rotate by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
		JAVA_INT.withName("n"),
	)

	val lua_copy by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("From Index"),
		JAVA_INT.withName("To Index"),
	)

	val lua_checkstack by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Amount"),
		ADDRESS.withName("char* message")
	)

	val lua_xmove by voidMethod(
		LUA_STATE.withName("From"),
		LUA_STATE.withName("To"),
		JAVA_INT.withName("n"),
	)

	//#endregion

	//#region access functions (stack -> C)

	private inline fun chFunc ()
		= method(JAVA_INT, LUA_STATE, JAVA_INT.withName("Index"))

	val lua_isnumber by chFunc()
	val lua_isstring by chFunc()
	val lua_iscfunction by chFunc()
	val lua_isinteger by chFunc()
	val lua_isuserdata by chFunc()
	val lua_type by chFunc()
	val lua_typename by method(
		ADDRESS.withName("Type Name"),
		LUA_STATE,
		JAVA_INT.withName("tp"),
	)

	val lua_tonumberx by method(
		JAVA_DOUBLE.withName("Lua Number"),
		LUA_STATE,
		JAVA_INT.withName("index"),
		ADDRESS.withName("size_t* isnum"),
	)

	val lua_tointegerx by method(
		ValueLayout.JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("index"),
		ADDRESS.withName("size_t* isnum"),
	)

	val lua_toboolean by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("index"),
	)

	val lua_tolstring by method(
		ADDRESS,
		LUA_STATE,
		JAVA_INT.withName("index"),
		ADDRESS.withName("size_t* len"),
	)

	val lua_rawlen by method(
		ValueLayout.JAVA_INT.withName("Lua Unsigned"),
		LUA_STATE,
		JAVA_INT.withName("index"),
	)

	val lua_tocfunction by method(
		ADDRESS.withName("lua_CFunction"),
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	val lua_touserdata by method(
		ADDRESS,
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	val lua_tothread by method(
		LUA_STATE,
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	val lua_topointer by method(
		ADDRESS.withName("const void*"),
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	//#endregion

	//#region Comparison and arithmetic functions

	val lua_arith by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Op")
	)

	val lua_rawequal by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Index 1"),
		JAVA_INT.withName("Index 2"),
	)

	val lua_compare by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Index 1"),
		JAVA_INT.withName("Index 2"),
		JAVA_INT.withName("Op"),
	)

	//#endregion

	//#region push functions (C -> stack)

	val lua_pushnil by voidMethod(LUA_STATE)
	val lua_pushnumber by voidMethod(LUA_STATE, JAVA_DOUBLE.withName("Lua Number"))
	val lua_pushinteger by voidMethod(LUA_STATE, JAVA_INT)
	val lua_pushlstring by method(
		ADDRESS.withName("const char* internalPointer"),
		LUA_STATE,
		ADDRESS.withName("const char* str"),
		JAVA_LONG.withName("len"),
	)
	val lua_pushstring by method(
		ADDRESS.withName("const char*? internalPointer"),
		LUA_STATE,
		ADDRESS.withName("const char* nullTermStr"),
	)
	// lua_pushvfstring and lua_pushfstring not supported here

	val lua_pushcclosure by voidMethod(
		LUA_STATE,
		ADDRESS.withName("lua_CFunction f"),
		JAVA_INT
	)

	val lua_pushboolean by voidMethod(
		LUA_STATE,
		JAVA_INT
	)

	val lua_pushlightuserdata by voidMethod(
		LUA_STATE,
		ADDRESS
	)

	val lua_pushthread by method(
		JAVA_INT,
		LUA_STATE,
	)

	//#endregion

	//#region get functions (Lua -> stack)

	val lua_getglobal by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("const char* name")
	)

	val lua_gettable by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Index")
	)

	val lua_getfield by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("tableIndex"),
		ADDRESS.withName("const char* key"),
	)

	val lua_geti by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("tableIndex"),
		ValueLayout.JAVA_INT.withName("indexInto"),
	)

	val lua_rawget by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("index"),
	)

	val lua_rawgeti by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("tableIndex"),
		ValueLayout.JAVA_INT.withName("indexInto"),
	)

	val lua_rawgetp by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("index"),
		ADDRESS.withName("const void* p"),
	)

	val lua_createtable by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("narr"),
		JAVA_INT.withName("nrec"),
	)

	val lua_newuserdatauv by method(
		ADDRESS,
		LUA_STATE,
		JAVA_LONG.withName("sz"),
		JAVA_INT.withName("n(ew)u(ser)value"),
	)

	val lua_getmetatable by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("objindex"),
	)

	val lua_getiuservalue by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("idx"),
		JAVA_INT.withName("n"),
	)

	//#endregion

	//#region Set functions (stack -> Lua)

	val lua_setglobal by voidMethod(
		LUA_STATE,
		ADDRESS.withName("const char* name"),
	)

	val lua_settable by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	val lua_setfield by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
		ADDRESS.withName("const char* key"),
	)

	val lua_seti by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
		ValueLayout.JAVA_INT.withName("n"),
	)

	val lua_rawset by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
	)

	val lua_rawseti by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
		ValueLayout.JAVA_INT.withName("n"),
	)

	val lua_rawsetp by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("Index"),
		ADDRESS.withName("const void* p"),
	)

	val lua_setmetatable by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Object Index")
	)

	val lua_setiuservalue by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("Index"),
		JAVA_INT.withName("n"),
	)

	//#endregion

	//#region uhhh

	val lua_callk by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("nargs"),
		JAVA_INT.withName("nresults"),
		ADDRESS.withName("lua_KContext ctx"),
		ADDRESS.withName("lua_KFunction k"),
	)

	val lua_pcallk by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("nargs"),
		JAVA_INT.withName("nresults"),
		JAVA_INT.withName("msgh"),
		ADDRESS.withName("lua_KContext ctx"),
		ADDRESS.withName("lua_KFunction k"),
	)

	val lua_load by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("lua_Reader reader"),
		ADDRESS.withName("void* dt"),
		ADDRESS.withName("const char* chunkName"),
		ADDRESS.withName("const char* mode"),
	)

	val lua_dump by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("lua_LuaWriter writer"),
		ADDRESS.withName("void* data"),
		JAVA_INT.withName("strip"),
	)

	val luaL_loadfilex by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("filename"),
		ADDRESS.withName("mode"),
	)

	//#endregion

	//#region warning related functions

	val lua_setwarnf by voidMethod(
		LUA_STATE,
		ADDRESS.withName("lua_WarnFunction f"),
		ADDRESS.withName("userdata"),
	)

	val lua_warning by voidMethod(
		LUA_STATE,
		ADDRESS.withName("const char* message"),
		JAVA_INT.withName("toCont")
	)

	//#endregion


	//#region misc functions

	val lua_error by method(JAVA_INT, LUA_STATE)
	val lua_next by method(JAVA_INT, LUA_STATE, JAVA_INT)
	val lua_concat by voidMethod(LUA_STATE, JAVA_INT)
	val lua_len by voidMethod(LUA_STATE, JAVA_INT)
	val lua_stringtonumber by method(
		JAVA_LONG,
		LUA_STATE,
		ADDRESS.withName("const char*")
	)


	//#endregion

	//#endregion


	//#region lauxlib.h

	val luaL_ref by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT.withName("t"),
	)

	val luaL_unref by voidMethod(
		LUA_STATE,
		JAVA_INT.withName("t"),
		JAVA_INT.withName("ref"),
	)

	val luaL_loadstring by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("const char* s")
	)

	val luaL_newmetatable by method(
		JAVA_INT,
		LUA_STATE,
		ADDRESS.withName("const char* s"),
	)

	val luaL_setmetatable by voidMethod(
		LUA_STATE,
		ADDRESS.withName("const char* s"),
	)

	//#endregion

	//#region lualib.h

	private inline fun toOpenLib ()
		= method(JAVA_INT, LUA_STATE)

	val luaopen_base by toOpenLib()

	val LUA_COLIBNAME = "coroutine"
	val LUA_TABLIBNAME = "table"
	val LUA_IOLIBNAME = "io"
	val LUA_OSLIBNAME = "os"
	val LUA_STRLIBNAME = "string"
	val LUA_UTF8LIBNAME = "utf8"
	val LUA_MATHLIBNAME = "math"
	val LUA_DBLIBNAME = "debug"
	val LUA_LOADLIBNAME = "package"

	val luaopen_coroutine by toOpenLib()
	val luaopen_table by toOpenLib()
	val luaopen_io by toOpenLib()
	val luaopen_os by toOpenLib()
	val luaopen_string by toOpenLib()
	val luaopen_utf8 by toOpenLib()
	val luaopen_math by toOpenLib()
	val luaopen_debug by toOpenLib()
	val luaopen_package by toOpenLib()

	/**
	* open all previous libraries
	*/
	val luaL_openlibs by voidMethod(LUA_STATE)


	//#endregion

	//#region luacoyote.h

	val luacoyote_get_registry_index by method(JAVA_INT)
	val luacoyote_get_ridx_mainthread by method(JAVA_INT)
	val luacoyote_get_ridx_globals by method(JAVA_INT)
	val luacoyote_get_ridx_last by method(JAVA_INT)


	val luacoyote_get_upval_index by method(
		JAVA_INT,
		JAVA_INT,
	)

	val luacoyote_call by voidMethod(
		LUA_STATE,
		JAVA_INT,
		JAVA_INT,
	)

	val luacoyote_pcall by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT,
		JAVA_INT,
		JAVA_INT,
	)


	val luacoyote_newtable by voidMethod(
		LUA_STATE,
	)

	val luacoyote_pop by voidMethod(
		LUA_STATE,
		JAVA_INT,
	)

	val luacoyote_register by voidMethod(
		LUA_STATE,
		ADDRESS.withName("const char* name"),
		ADDRESS.withName("lua_CFunction"),
	)

	val luacoyote_pushcfunction by voidMethod(
		LUA_STATE,
		ADDRESS.withName("lua_CFunction")
	)


	val luacoyote_isfunction by chFunc()
	val luacoyote_istable by chFunc()
	val luacoyote_islightuserdata by chFunc()
	val luacoyote_isnil by chFunc()
	val luacoyote_isboolean by chFunc()
	val luacoyote_isthread by chFunc()
	val luacoyote_isnone by chFunc()
	val luacoyote_isnoneornil by chFunc()

	val luaL_checknumber by method(
		JAVA_DOUBLE,
		LUA_STATE,
		JAVA_INT,
	)

	val luaL_checkinteger by method(
		JAVA_INT,
		LUA_STATE,
		JAVA_INT,
	)

	//#endregion


	val luaL_checklstring by method(
		ADDRESS.withName("const char*"),
		LUA_STATE,
		JAVA_INT,
		ADDRESS.withName("size_t* l"),
	)

	internal val luaL_gettypenametable by method(
		ADDRESS.withName("const char* const*"),
		ADDRESS.withName("size_t* tablesize"),
		ADDRESS.withName("size_t* longestname"),
	)

}