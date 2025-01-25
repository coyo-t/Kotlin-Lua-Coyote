/*
** $Id: lmathlib.c $
** Standard mathematical library
** See Copyright Notice in lua.h
*/

#define lmathlib_c
#define LUA_LIB

#include "lprefix.hpp"


#include <cfloat>
#include <climits>
#include <cmath>
#include <cstdlib>

#include "lua.hpp"

#include "lauxlib.hpp"
#include "lualib.hpp"


#undef PI
static constexpr auto PI =	3.141592653589793238462643383279502884;

static constexpr auto TAU = PI * 2.0;

static constexpr auto DEGTORAD = (180.0 / PI);
static constexpr auto RADTODEG = (PI / 180.0);


static int math_abs(lua_State *L)
{
	if (lua_isinteger(L, 1))
	{
		lua_Integer n = lua_tointeger(L, 1);
		if (n < 0) n = (lua_Integer) (0u - (lua_Unsigned) n);
		lua_pushinteger(L, n);
	}
	else
		lua_pushnumber(L, l_mathop(fabs)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_sin(lua_State *L)
{
	lua_pushnumber(L, l_mathop(sin)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_cos(lua_State *L)
{
	lua_pushnumber(L, l_mathop(cos)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_tan(lua_State *L)
{
	lua_pushnumber(L, l_mathop(tan)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_asin(lua_State *L)
{
	lua_pushnumber(L, l_mathop(asin)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_acos(lua_State *L)
{
	lua_pushnumber(L, l_mathop(acos)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_atan(lua_State *L)
{
	lua_Number y = luaL_checknumber(L, 1);
	lua_Number x = luaL_optnumber(L, 2, 1);
	lua_pushnumber(L, l_mathop(atan2)(y, x));
	return 1;
}


static int math_toint(lua_State *L)
{
	int valid;
	lua_Integer n = lua_tointegerx(L, 1, &valid);
	if (l_likely(valid))
		lua_pushinteger(L, n);
	else
	{
		luaL_checkany(L, 1);
		luaL_pushfail(L); /* value is not convertible to integer */
	}
	return 1;
}


static void pushnumint(lua_State *L, lua_Number d)
{
	lua_Integer n;
	if (lua_numbertointeger(d, &n)) /* does 'd' fit in an integer? */
		lua_pushinteger(L, n); /* result is integer */
	else
		lua_pushnumber(L, d); /* result is float */
}


static int math_floor(lua_State *L)
{
	if (lua_isinteger(L, 1))
		lua_settop(L, 1); /* integer is its own floor */
	else
	{
		lua_Number d = l_mathop(floor)(luaL_checknumber(L, 1));
		pushnumint(L, d);
	}
	return 1;
}


static int math_ceil(lua_State *L)
{
	if (lua_isinteger(L, 1))
		lua_settop(L, 1); /* integer is its own ceil */
	else
	{
		lua_Number d = l_mathop(ceil)(luaL_checknumber(L, 1));
		pushnumint(L, d);
	}
	return 1;
}


static int math_fmod(lua_State *L)
{
	if (lua_isinteger(L, 1) && lua_isinteger(L, 2))
	{
		lua_Integer d = lua_tointeger(L, 2);
		if (static_cast<lua_Unsigned>(d) + 1u <= 1u)
		{
			/* special cases: -1 or 0 */
			luaL_argcheck(L, d != 0, 2, "zero");
			lua_pushinteger(L, 0); /* avoid overflow with 0x80000... / -1 */
		}
		else
			lua_pushinteger(L, lua_tointeger(L, 1) % d);
	}
	else
		lua_pushnumber(L, l_mathop(fmod)(luaL_checknumber(L, 1),
													luaL_checknumber(L, 2)));
	return 1;
}


/*
** next function does not use 'modf', avoiding problems with 'double*'
** (which is not compatible with 'float*') when lua_Number is not
** 'double'.
*/
static int math_modf(lua_State *L)
{
	if (lua_isinteger(L, 1))
	{
		lua_settop(L, 1); /* number is its own integer part */
		lua_pushnumber(L, 0); /* no fractional part */
	}
	else
	{
		lua_Number n = luaL_checknumber(L, 1);
		/* integer part (rounds toward zero) */
		lua_Number ip = (n < 0) ? l_mathop(ceil)(n) : l_mathop(floor)(n);
		pushnumint(L, ip);
		/* fractional part (test needed for inf/-inf) */
		lua_pushnumber(L, (n == ip) ? l_mathop(0.0) : (n - ip));
	}
	return 2;
}


static int math_sqrt(lua_State *L)
{
	lua_pushnumber(L, l_mathop(sqrt)(luaL_checknumber(L, 1)));
	return 1;
}


static int math_ult(lua_State *L)
{
	lua_Integer a = luaL_checkinteger(L, 1);
	lua_Integer b = luaL_checkinteger(L, 2);
	lua_pushboolean(L, (lua_Unsigned) a < (lua_Unsigned) b);
	return 1;
}

static int math_log(lua_State *L)
{
	lua_Number x = luaL_checknumber(L, 1);
	lua_Number res;
	if (lua_isnoneornil(L, 2))
		res = l_mathop(log)(x);
	else
	{
		lua_Number base = luaL_checknumber(L, 2);
		if (base == l_mathop(10.0))
			res = l_mathop(log10)(x);
		else
			res = l_mathop(log)(x) / l_mathop(log)(base);
	}
	lua_pushnumber(L, res);
	return 1;
}

static int math_exp(lua_State *L)
{
	lua_pushnumber(L, l_mathop(exp)(luaL_checknumber(L, 1)));
	return 1;
}

static int math_deg(lua_State *L)
{
	lua_pushnumber(L, luaL_checknumber(L, 1) * DEGTORAD);
	return 1;
}

static int math_rad(lua_State *L)
{
	lua_pushnumber(L, luaL_checknumber(L, 1) * RADTODEG);
	return 1;
}


static int math_min(lua_State *L)
{
	int n = lua_gettop(L); /* number of arguments */
	int imin = 1; /* index of current minimum value */
	luaL_argcheck(L, n >= 1, 1, "value expected");
	for (int i = 2; i <= n; i++)
	{
		if (lua_compare(L, i, imin, LuaCompareOp::LT))
			imin = i;
	}
	lua_pushvalue(L, imin);
	return 1;
}


static int math_max(lua_State *L)
{
	int n = lua_gettop(L); /* number of arguments */
	int imax = 1; /* index of current maximum value */
	int i;
	luaL_argcheck(L, n >= 1, 1, "value expected");
	for (i = 2; i <= n; i++)
	{
		if (lua_compare(L, imax, i, LuaCompareOp::LT))
			imax = i;
	}
	lua_pushvalue(L, imax);
	return 1;
}


static int math_type(lua_State *L)
{
	if (lua_type(L, 1) == LUA_TNUMBER)
		lua_pushstring(L, (lua_isinteger(L, 1)) ? "integer" : "float");
	else
	{
		luaL_checkany(L, 1);
		luaL_pushfail(L);
	}
	return 1;
}


static int math_dsin(lua_State *L)
{
	lua_pushnumber(L, l_mathop(sin)(luaL_checknumber(L, 1) * DEGTORAD));
	return 1;
}

static int math_dcos(lua_State *L)
{
	lua_pushnumber(L, l_mathop(cos)(luaL_checknumber(L, 1) * DEGTORAD));
	return 1;
}

static int math_dtan(lua_State *L)
{
	lua_pushnumber(L, l_mathop(tan)(luaL_checknumber(L, 1) * DEGTORAD));
	return 1;
}


static int math_sico (lua_State *L)
{
	lua_Number angle = luaL_checknumber(L, 1);

	lua_pushnumber(L, sin(angle));
	lua_pushnumber(L, cos(angle));


	return 2;
}


#include "./random/random.cpp"


static const luaL_Reg mathlib[] = {
	{"abs", math_abs},
	{"acos", math_acos},
	{"asin", math_asin},
	{"atan", math_atan},
	{"ceil", math_ceil},
	{"cos", math_cos},
	{"deg", math_deg},
	{"exp", math_exp},
	{"tointeger", math_toint},
	{"floor", math_floor},
	{"fmod", math_fmod},
	{"ult", math_ult},
	{"log", math_log},
	{"max", math_max},
	{"min", math_min},
	{"modf", math_modf},
	{"rad", math_rad},
	{"sin", math_sin},
	{"sqrt", math_sqrt},
	{"tan", math_tan},
	{"type", math_type},

	{"dsin", math_dsin},
	{"dcos", math_dcos},
	{"dtan", math_dtan},
	{"sico", math_sico},

	/* placeholders */
	{"random", NULL},
	{"randomseed", NULL},
	{"pi", NULL},
	{"huge", NULL},
	{"maxinteger", NULL},
	{"mininteger", NULL},
	{NULL, NULL}
};


/*
** Open math library
*/
LUAMOD_API int luaopen_math(lua_State *L)
{
	luaL_newlib(L, mathlib);
	lua_pushnumber(L, PI);
	lua_setfield(L, -2, "pi");
	lua_pushnumber(L, (lua_Number) HUGE_VAL);
	lua_setfield(L, -2, "huge");
	lua_pushinteger(L, LUA_MAXINTEGER);
	lua_setfield(L, -2, "maxinteger");
	lua_pushinteger(L, LUA_MININTEGER);
	lua_setfield(L, -2, "mininteger");
	setrandfunc(L);
	return 1;
}
