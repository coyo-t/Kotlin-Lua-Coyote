package com.catsofwar.lua.exceptions

import com.catsofwar.lua.LuaCoyote

class LuaTypeException (val L: LuaCoyote, message: String): Exception(message)
