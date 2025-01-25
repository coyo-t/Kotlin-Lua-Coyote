package com.catsofwar.lua

import com.catsofwar.lua.enums.LuaBuiltinLibrary
import com.catsofwar.lua.enums.LuaError
import com.catsofwar.lua.enums.LuaType
import com.catsofwar.lua.exceptions.LuaTypeException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.*
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.math.floor


@JvmInline
value class LuaCoyote
internal constructor(internal val state: MemorySegment) : AutoCloseable, Arena
{
	constructor (doOpenBaseLibrary: Boolean = true) : this(createState())
	{
		if (doOpenBaseLibrary)
		{
			openBaseLibrary()
		}
	}

	//#region type checks

	fun isString(index: Int) = (dll.lua_isstring(state, index) as Int) != 0
	fun isNumber(index: Int) = (dll.lua_isnumber(state, index) as Int) != 0
	fun isInteger(index: Int) = (dll.lua_isinteger(state, index) as Int) != 0
	fun isCFunction(index: Int) = (dll.lua_iscfunction(state, index) as Int) != 0
	fun isUserdata(index: Int) = (dll.lua_isuserdata(state, index) as Int) != 0
	fun isFunction(index: Int) = (dll.luacoyote_isfunction(state, index) as Int) != 0
	fun isTable(index: Int) = (dll.luacoyote_istable(state, index) as Int) != 0
	fun isLightUserdata(index: Int) = (dll.luacoyote_islightuserdata(state, index) as Int) != 0
	fun isNil(index: Int) = (dll.luacoyote_isnil(state, index) as Int) != 0
	fun isBoolean(index: Int) = (dll.luacoyote_isboolean(state, index) as Int) != 0
	fun isThread(index: Int) = (dll.luacoyote_isthread(state, index) as Int) != 0
	fun isNone(index: Int) = (dll.luacoyote_isnone(state, index) as Int) != 0
	fun isNoneOrNil(index: Int) = (dll.luacoyote_isnoneornil(state, index) as Int) != 0

	//#endregion


	fun concat(count: Int)
	{
		dll.lua_concat(state, count)
	}

	fun setField(index: Int, name: String)
	{
		confinedArena { f ->
			dll.lua_setfield(state, index, f.allocateFrom(name))
		}
	}

	fun newUserData(sizeof: Long, uservalues: Int): MemorySegment
	{
		return dll.lua_newuserdatauv(state, sizeof, uservalues) as MemorySegment
	}

	fun getUserValue(stackIndex: Int, userIndex: Int): Int
	{
		return dll.lua_getiuservalue(state, stackIndex, userIndex) as Int
	}

	fun setUserValue(stackIndex: Int, userIndex: Int): Int
	{
		return dll.lua_setiuservalue(state, stackIndex, userIndex) as Int
	}

	fun asUserdata(index: Int): MemorySegment
	{
		return dll.lua_touserdata(state, index) as MemorySegment
	}

	fun asBoolean(index: Int): Boolean
	{
		return (dll.lua_toboolean(state, index) as Int) != 0
	}

	fun checkStack(amount: Int)
	{
		if (dll.lua_checkstack(state, amount, MemorySegment.NULL) as Int == 0)
		{
			throw RuntimeException("Lua state out of memory")
		}
	}

	fun asStringMemorySegment(index: Int): MemorySegment
	{
		confinedArena { arena ->
			val slen = arena.allocate(JAVA_INT)
			val adr = dll.lua_tolstring(state, index, slen) as MemorySegment
			if (adr == MemorySegment.NULL)
			{
				return MemorySegment.NULL
			}
			val sls = slen[JAVA_INT, 0L]
			if (sls <= 0)
			{
				return MemorySegment.NULL
			}
			return adr.reinterpret(sls.toLong()).asReadOnly()
		}
	}

	fun asString(index: Int): String?
	{
		confinedArena { arena ->
			val slen = arena.allocate(JAVA_INT)
			val adr = dll.lua_tolstring(state, index, slen) as MemorySegment
			if (adr == MemorySegment.NULL)
			{
				return null
			}
			val sls = slen[JAVA_INT, 0L]
			if (sls <= 0)
			{
				return ""
			}

			return String(adr.reinterpret(sls.toLong()).toArray(JAVA_BYTE))
		}
	}

	fun asNumber(index: Int): Double
	{
		return dll.lua_tonumberx(state, index, MemorySegment.NULL) as Double
	}

	fun asNumberExt(index: Int): Double?
	{
		confinedArena { arena ->
			val wass = arena.allocate(JAVA_INT)
			val outs = dll.lua_tonumberx(state, index, wass) as Double
			if (wass[JAVA_INT, 0L] != 0)
				return null
			return outs
		}
	}

	fun asInteger(index: Int): Int
	{
		return dll.lua_tointegerx(state, index, MemorySegment.NULL) as Int
	}

	fun asIntegerOrNull(index: Int): Int?
	{
		confinedArena { arena ->
			val wass = arena.allocate(JAVA_INT)
			val outs = dll.lua_tointegerx(state, index, wass) as Int
			if (wass[JAVA_INT, 0L] != 0)
				return outs
			return null
		}
	}

	fun pCall(nargs: Int, nresults: Int = -1, msgHandler: Int = 0): LuaError
	{
		return LuaError(dll.luacoyote_pcall(state, nargs, nresults, msgHandler) as Int)
	}

	fun pCallOrThrow(argc: Int, resc: Int = -1)
	{
		OHFUCK(pCall(argc, resc))
	}

	fun loadFile(path: Path): Int
	{
		return loadFile(path.absolutePathString())
	}

	fun loadFile(filename: String): Int
	{
		confinedArena { arena ->
			return dll.luaL_loadfilex(state, arena.allocateFrom(filename), MemorySegment.NULL) as Int
		}
	}

	override fun close()
	{
		dll.lua_close(state)
	}


	var top: Int
		get()
		{
			return dll.lua_gettop(state) as Int
		}
		set(value)
		{
			dll.lua_settop(state, value)
		}

	fun toAbsoluteIndex(index: Int): Int
	{
		require(index != 0) { "Stack index should not be 0" }
		if (index > 0)
		{
			return index
		}
		if (index <= registryIndex)
		{
			return index
		}
		return top + 1 + index
	}

	fun getGlobal(name: String): LuaType
	{
		confinedArena { arena ->
			return LuaType(dll.lua_getglobal(state, arena.allocateFrom(name)) as Int)
		}
	}

	inline fun <T> getGlobal(name: String, bloc: LuaCoyote.(type: LuaType) -> T): T
	{
		val tell = top
		return bloc(getGlobal(name)).also { top = tell }
	}


	fun pop(count: Int = 1)
	{
		if (top == 0)
			throw RuntimeException("Stack underflow!")
		dll.luacoyote_pop(state, count)
	}

	fun openLibraries()
	{
		dll.luaL_openlibs(state)
	}


	fun openBaseLibrary()
	{
		dll.luaopen_base(state)
	}

	fun openRegisterBuiltinLibrary(vararg libs: LuaBuiltinLibrary)
	{
		libs.forEach(::openRegisterBuiltinLibrary)
	}

	fun openRegisterBuiltinLibrary(what: LuaBuiltinLibrary)
	{
		openBuiltinLibrary(what)
		if (what.lname.isNotEmpty())
		{
			setGlobal(what.lname)
		}
	}

	fun openBuiltinLibrary(what: LuaBuiltinLibrary)
	{
		when (what)
		{
			LuaBuiltinLibrary.BASE -> dll.luaopen_base(state)
			LuaBuiltinLibrary.MATH -> dll.luaopen_math(state)
			LuaBuiltinLibrary.COROUTINE -> dll.luaopen_coroutine(state)
			LuaBuiltinLibrary.TABLE -> dll.luaopen_table(state)
			LuaBuiltinLibrary.IO -> dll.luaopen_io(state)
			LuaBuiltinLibrary.OS -> dll.luaopen_os(state)
			LuaBuiltinLibrary.STRING -> dll.luaopen_string(state)
			LuaBuiltinLibrary.UTF8 -> dll.luaopen_utf8(state)
			LuaBuiltinLibrary.DEBUG -> dll.luaopen_debug(state)
			LuaBuiltinLibrary.PACKAGE -> dll.luaopen_package(state)
		}
	}

	fun openBuiltinLibrary(vararg libs: LuaBuiltinLibrary)
	{
		libs.forEach(this::openBuiltinLibrary)
	}

	fun pushString(s: String)
	{
		confinedArena { f ->
			dll.lua_pushstring(state, f.allocateFrom(s))
		}
	}

	fun pushNumber(i: Number)
	{
		pushNumber(i.toDouble())
	}

	fun pushNumber(i: Double)
	{
		checkStack(1)
		dll.lua_pushnumber(state, i)
	}

	fun pushInteger(i: Int)
	{
		checkStack(1)
		dll.lua_pushinteger(state, i)
	}

	fun pushNil()
	{
		checkStack(1)
		dll.lua_pushnil(state)
	}

	fun pushValue(srcIndex: Int)
	{
		checkStack(1)
		dll.lua_pushvalue(state, srcIndex)
	}

	fun pushFuncUpval(upvalIndex: Int)
	{
		checkStack(1)
		pushValue(getUpvalIndex(upvalIndex))
	}

	fun pushBoolean(value: Boolean)
	{
		dll.lua_pushboolean(state, if (value) 1 else 0)
	}

	fun next(index: Int): Boolean
	{
		checkStack(1)
		return (dll.lua_next(state, index) as Int) != 0
	}

	fun typeNameOf(index: Int): String
	{
		val typen = dll.lua_type(state, index) as Int
		val res = dll.lua_typename(state, typen) as MemorySegment
		return res.reinterpret(Int.MAX_VALUE.toLong()).getString(0L)
	}

	fun typeOf(index: Int): LuaType
	{
		return LuaType(dll.lua_type(state, index) as Int)
	}

	fun rawGetI(tableIndex: Int, indexInto: Int): LuaType
	{
		checkStack(1)
		return LuaType(dll.lua_rawgeti(state, tableIndex, indexInto) as Int)
	}

	fun rawSetI(tableIndex: Int, indexInto: Int)
	{
		dll.lua_rawseti(state, tableIndex, indexInto)
	}

	fun rawLength(index: Int): Int
	{
		return dll.lua_rawlen(state, index) as Int
	}

	//#region refs

	fun ref(index: Int) = dll.luaL_ref(state, index) as Int

	fun ref() = ref(registryIndex)

	fun refGetGlobal()
	{
		refGet(dll.luacoyote_get_ridx_globals() as Int)
	}

	fun refGet(ref: Int) = rawGetI(registryIndex, ref)

	fun unref(ref: Int)
	{
		unref(registryIndex, ref)
	}

	fun unref(index: Int, ref: Int)
	{
		dll.luaL_unref(state, index, ref)
	}

	fun newMetaTable(name: String): Boolean
	{
		confinedArena {
			val outs = dll.luaL_newmetatable(state, it.allocateFrom(name))
			return (outs as Int) == 1
		}
	}

	inline fun newMetaTable(name: String, ifNotExist: LuaCoyote.(mindex: Int) -> Unit)
	{
		val tell = top
		if (newMetaTable(name))
		{
			ifNotExist(this, toAbsoluteIndex(-1))
		}
		top = tell
	}

	fun setMetaTable(stackIndex: Int)
	{
		dll.lua_setmetatable(state, stackIndex)
	}

	fun setMetaTable(name: String)
	{
		confinedArena {
			dll.luaL_setmetatable(state, it.allocateFrom(name))
		}
	}

	fun getMetaTable(index: Int): Boolean
	{
		return (dll.lua_getmetatable(state, index) as Int) != 0
	}

	fun getMetaTable(name: String) = getField(registryIndex, name)

	fun setFuncs(vararg funcs: Pair<String, LuaCFunction?>)
	{
		for ((name, func) in funcs)
		{
			if (func == null)
			{
				// placeholder?
				pushBoolean(false)
			}
			else
			{
				pushClosure(func)
			}
			setField(-2, name)
		}
	}

	//#endregion

	fun pushClosure(f: LuaCFunction)
	{
		pushClosure(0, f)
	}

	fun pushClosure(upvalueCount: Int = 0, f: LuaCFunction)
	{
		if (upvalueCount >= 255)
		{
			throw IllegalArgumentException("Too Many upvalues!")
		}

		checkStack(1)

		val handle = linker.upcallStub(
			LuaDll.invokeHandle.bindTo(f),
			LuaDll.invokeDesc,
			this
		)

		if (upvalueCount == 0)
		{
			dll.luacoyote_pushcfunction(state, handle)
			return
		}
		dll.lua_pushcclosure(state, handle, upvalueCount)
	}

	fun register(name: String, upvalueCount: Int, f: LuaCFunction)
	{
		checkStack(1)
		pushClosure(upvalueCount, f)
		setGlobal(name)
	}

	fun register(name: String, f: LuaCFunction)
	{
		register(name, 0, f)
	}

	fun setGlobal(name: String)
	{
		confinedArena { arena ->
			dll.lua_setglobal(state, arena.allocateFrom(name))
		}
	}

	fun getField(index: Int, name: String): Int
	{
		checkStack(1)
		confinedArena { f ->
			dll.lua_getfield(state, index, f.allocateFrom(name))
			return toAbsoluteIndex(-1)
		}
	}

	inline fun <T> getField(
		index: Int,
		name: String,
		bloc: LuaCoyote.(stackIndex: Int) -> T
	): T
	{
		val tell = top
		checkStack(1)
		getField(index, name)
		return bloc(toAbsoluteIndex(-1)).also { top = tell }
	}

	fun getTable(index: Int): LuaType
	{
		return LuaType(dll.lua_gettable(state, index) as Int)
	}

	fun newTable(arrayCount: Int, keyValueCount: Int)
	{
		checkStack(1)
		dll.lua_createtable(state, arrayCount, keyValueCount)
	}

	fun newTable()
	{
		checkStack(1)
		dll.luacoyote_newtable(state)
	}

	inline fun newTable(name: String, setup: (index: Int) -> Unit)
	{
		newTable()
		val tell = top
		setup(toAbsoluteIndex(-1))
		top = tell
		setGlobal(name)
	}


	fun doString(pointer: MemorySegment, returnCount: Int = -1): LuaError
	{
		if (pointer == MemorySegment.NULL)
			throw RuntimeException("Null pointer given to doString")

		if (dll.luaL_loadstring(state, pointer) as Int != 0)
		{
			return LuaError.SyntaxError
		}
		if (dll.luacoyote_pcall(state, 0, returnCount, 0) as Int != 0)
		{
			return LuaError.RuntimeError
		}
		return LuaError.Ok
	}

	fun doStringLoudly(s: String, returnCount: Int = -1)
	{
		OHFUCK(doString(s, returnCount))
	}

	fun doStringLoudly(p: Path, returnCount: Int = -1)
	{
		doStringLoudly(p.readText(Charsets.US_ASCII), returnCount)
	}

	fun doConfigFileLoudly(p: Path, configObjectName: String): Int
	{
		val tell = top
		newTable()
		setGlobal(configObjectName)
		doStringLoudly(p, 0)
		top = tell
		getGlobal(configObjectName)
		return toAbsoluteIndex(-1)
	}

	fun doConfigFileLoudly(
		p: Path,
		configObjectName: String,
		confSetup: LuaCoyote.(i: Int) -> Unit
	): Int
	{
		newTable()
		confSetup(toAbsoluteIndex(-1))
		setGlobal(configObjectName)
		doStringLoudly(p, 0)
		getGlobal(configObjectName)
		return toAbsoluteIndex(-1)
	}

	fun doString(s: String, returnCount: Int = -1): LuaError
	{
		checkNotNull(s) { "Null string given to doString" }
		confinedArena { f ->
			val txt = f.allocateFrom(s)
			return doString(txt, returnCount)
		}
	}

	fun loadString(s: String?): LuaError
	{
		checkNotNull(s) { "Null string given to loadString" }
		confinedArena { f ->
			val pointer = f.allocateFrom(s)
			if (pointer == MemorySegment.NULL)
				throw RuntimeException("Null pointer given to doString")
			return LuaError(dll.luaL_loadstring(state, pointer) as Int)
		}
	}

	fun loadStringLoudly(s: String?)
	{
		OHFUCK(loadString(s))
	}


	fun doFile(path: Path, returnCount: Int = -1): LuaError
	{
		if (!path.exists())
		{
			throw NoSuchFileException(path.toFile())
		}
		confinedArena { f ->
			val pathStr = f.allocateFrom(path.absolutePathString())
			if (dll.luaL_loadfilex(state, pathStr, MemorySegment.NULL) as Int != 0)
			{
				return LuaError.SyntaxError
			}
			if (dll.luacoyote_pcall(state, 0, returnCount, 0) as Int != 0)
			{
				return LuaError.RuntimeError
			}
			return LuaError.Ok
		}
	}


	fun OHFUCK(res: LuaError)
	{
		if (!res.isOkay())
		{
			traceback()
			throw RuntimeException(asString(-1))
		}
	}


	fun castToInteger(index: Int): Int
	{
		if (isInteger(index))
		{
			return asInteger(index)
		}
		return floor(asNumber(index)).toInt()
	}

	fun call(argc: Int, retc: Int = -1)
	{
		TODO("This one's busted???")
		dll.luacoyote_call(state, argc, retc)
	}

	fun traceback()
	{
		if (!isString(1)) // message isn't a string?
			return // keep intact

		if (getGlobal("debug") != LuaType.TABLE)
		{
			pop(1)
			return
		}

		getField(-1, "traceback")
		if (!isFunction(-1))
		{
			pop(2)
			return
		}

		pushValue(1) // pass error message
		pushInteger(2) // skip this func and traceback
		pCall(2, 1) // call debug.traceback
	}

	/**
	 * the lua function is called `lua_topointer`
	 */
	fun getAddressOf(index: Int): MemorySegment
	{
		return dll.lua_topointer(state, index) as MemorySegment
	}

	/**
	 * Iterator version of tablePairs.
	 * Yields the absolute stack values of the key and value
	 */
	fun iterTablePairs(tableIndex: Int) = iterator {
		val absIndex = toAbsoluteIndex(tableIndex)
		if (!isTable(absIndex))
		{
			throw IllegalStateException("Expected a table, got ${typeNameOf(absIndex)}")
		}
		val tell = top
		pushNil()
		while (next(absIndex))
		{
			val tell2 = top
			yield(toAbsoluteIndex(-2) to toAbsoluteIndex(-1))
			top = tell2
			pop() // for L.next
		}
		top = tell
	}

	inline fun tablePairs(tableIndex: Int, runner: LuaCoyote.() -> Unit)
	{
		val absIndex = toAbsoluteIndex(tableIndex)
		if (typeOf(absIndex) != LuaType.TABLE)
		{
			throw IllegalStateException("You're gonna get an access violation doing that dingus!!!")
		}
		val tell = top
		pushNil()
		while (next(absIndex))
		{
			runner.invoke(this)
			pop() // for L.next
		}
		top = tell
	}

	inline fun <R> pushTop(runner: LuaCoyote.() -> R): R
	{
		return top.let { t -> runner(this).also { top = t } }
	}

	override fun allocate(byteSize: Long, byteAlignment: Long): MemorySegment
	{
		return newUserData(byteSize, 1).also { ref() }
	}

	override fun scope() = state.scope()

	fun forArrayStrings(index: Int) = sequence {
		checkStack(2)
		val realIndex = toAbsoluteIndex(index)
		val tell = top
		for (i in 1..rawLength(realIndex))
		{
			rawGetI(realIndex, i)
			pushValue(-1)
			val outs = asString(-1)
			if (outs != null)
			{
				yield(outs)
			}
			pop(2)
		}
		top = tell
	}

	//#region global setter utils

	fun setGlobalNil(name: String)
	{
		checkStack(1)
		pushNil()
		setGlobal(name)
	}

	fun setGlobalString(name: String, value: String)
	{
		checkStack(1)
		pushString(value)
		setGlobal(name)
	}

	fun setGlobalBoolean(name: String, value: Boolean)
	{
		checkStack(1)
		pushBoolean(value)
		setGlobal(name)
	}

	fun setGlobalCFunction(name: String, func: LuaCFunction)
	{
		checkStack(1)
		pushClosure(func)
		setGlobal(name)
	}


	//#endregion

	fun asIntegerChecked(stackIndex: Int): Int
	{
		val addr = toAbsoluteIndex(stackIndex)
		val outs = asIntegerOrNull(addr)

		if (outs == null)
		{
			val msg = if (isNumber(addr))
				"stack @$addr: number has no integer representation"
			else
				"stack @$addr is type '${typeNameOf(addr)}' (expected an integer)"
			pop()
			throw LuaTypeException(this, msg)
		}
		return outs
	}

	fun checkIntegerField(tableIndex: Int, fieldName: String): Int
	{
		val addr = toAbsoluteIndex(tableIndex)
		checkStack(1)
		getField(addr, fieldName)
		val outs = asIntegerOrNull(-1)

		if (outs == null)
		{
			val msg = if (isNumber(-1))
				"field '$fieldName': number has no integer representation"
			else
				"field $fieldName is type '${typeNameOf(-1)}' (expected an integer)"
			pop()
			throw LuaTypeException(this, msg)
		}
		return outs.also { pop() }
	}

	fun checkStringField(tableIndex: Int, fieldName: String): String
	{
		val addr = toAbsoluteIndex(tableIndex)
		checkStack(1)
		getField(addr, fieldName)
		if (!isString(-1))
		{
			val msk = "field $fieldName is type '${typeNameOf(-1)}' (expected ${LuaType.STRING.typeName})"
			pop()
			throw LuaTypeException(this, msk)
		}
		return asString(-1)!!.also { pop() }
	}

	fun rawIterTable(tableIndex: Int) = sequence {
		val tell = top
		val ptr = toAbsoluteIndex(tableIndex)
		val vind = ptr + 1
		for (i in 1..rawLength(ptr))
		{
			val tell = top
			rawGetI(ptr, i)
			yield(vind)
			top = tell
		}

		top = tell
	}

	@Throws(LuaTypeException::class)
	fun throwGeneralTypeError(expect: LuaType, gotAt: Int)
	{
		throw LuaTypeException(this, "expected ${expect.typeName}, got ${typeNameOf(toAbsoluteIndex(gotAt))}")
	}

	companion object
	{
		private var initialized = false

		@JvmStatic
		internal lateinit var dll: LuaDll
		internal var registryIndex: Int = 0

		private fun createState(): MemorySegment
		{
			__init()
			return dll.luaL_newstate() as MemorySegment
		}

		private fun __init()
		{
			if (initialized)
				return
			dll = LuaDll.createDllInst()
			registryIndex = dll.luacoyote_get_registry_index() as Int
			initialized = true
		}

		fun setupDll(callback: () -> Unit)
		{
			setupDll(false, callback)
		}

		fun setupDll(forceInit: Boolean, callback: () -> Unit)
		{
			LuaDll.initCallback = callback
			if (forceInit)
			{
				__init()
			}
		}

		fun getUpvalIndex(src: Int): Int
		{
			return dll.luacoyote_get_upval_index(src) as Int
		}

	}
}