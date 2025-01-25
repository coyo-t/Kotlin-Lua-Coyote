package com.catsofwar.lua

import java.lang.foreign.*
import java.lang.foreign.Arena.ofConfined
import java.lang.invoke.MethodHandle
import kotlin.reflect.KProperty

internal val linker = Linker.nativeLinker()


internal fun getMethod(name: String, desc: FunctionDescriptor): MethodHandle
{
	val dma = SymbolLookup.loaderLookup().find(name)
	check(dma.isPresent) { "Failure to look up method symbol $name" }
	return linker.downcallHandle(dma.get(), desc)
}


internal inline fun <R> confinedArena(block: (arena: Arena) -> R)
	= ofConfined().use(block)


internal class voidMethod(vararg argLayout: MemoryLayout)
{
	val vars = argLayout
	operator fun provideDelegate (self: Any?, prop: KProperty<*>): MethodHandleDelegate
	{
		return MethodHandleDelegate(getMethod(prop.name, FunctionDescriptor.ofVoid(*vars)))
	}
}

internal class method(val returns:MemoryLayout, vararg argLayout: MemoryLayout)
{
	val vars = argLayout
	operator fun provideDelegate (self: Any?, prop: KProperty<*>): MethodHandleDelegate
	{
		return MethodHandleDelegate(getMethod(prop.name, FunctionDescriptor.of(returns, *vars)))
	}
}

internal class MethodHandleDelegate (val mh: MethodHandle)
{
	operator fun getValue (self: Any?, property: KProperty<*>) = mh
}
