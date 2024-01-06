
package luaj.compiler;

import luaj.Globals;
import luaj.LuaClosure;
import luaj.LuaFunction;
import luaj.LuaString;
import luaj.LuaValue;
import luaj.Prototype;
import luaj.lib.BaseLib;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

public class LuaC extends Constants implements Globals.Compiler, Globals.Loader {

	/** A sharable instance of the LuaC compiler. */
	public static final LuaC instance = new LuaC();
	
	/** Install the compiler so that LoadState will first 
	 * try to use it when handed bytes that are 
	 * not already a compiled lua chunk.
	 * @param globals the Globals into which this is to be installed.
	 */
	public static void install(Globals globals) {
		globals.compiler = instance;
		globals.loader = instance;
	}

	protected LuaC() {}

	public Prototype compile(InputStream stream, String chunkname, Globals globals) throws IOException {
		return (new CompileState()).luaY_parser(stream, chunkname, globals);
	}

	public LuaFunction load(Prototype prototype, String chunkname, Globals globals, LuaValue env) throws IOException {
		return new LuaClosure(prototype, globals, env);
	}


	public LuaValue load(InputStream stream, String chunkname, Globals globals) throws IOException {
		return new LuaClosure(compile(stream, chunkname, globals), globals, globals);
	}

	static class CompileState {
		int nCcalls = 0;
		private Hashtable strings = new Hashtable();
		protected CompileState() {}
	
		/** Parse the input */
		private Prototype luaY_parser(InputStream z, String name, Globals globals) throws IOException{
			LexState lexstate = new LexState(this, z);
			FuncState funcstate = new FuncState();
			// lexstate.buff = buff;
			lexstate.fs = funcstate;
			lexstate.globals=globals;
			lexstate.setinput(this, z.read(), z, (LuaString) LuaValue.valueOf(name) );
			/* main func. is always vararg */
			funcstate.f = new Prototype();
			funcstate.f.source = (LuaString) LuaValue.valueOf(name);
			lexstate.mainfunc(funcstate);
			LuaC._assert (funcstate.prev == null);
			/* all scopes should be correctly finished */
			LuaC._assert (lexstate.dyd == null 
					|| (lexstate.dyd.n_actvar == 0 && lexstate.dyd.n_gt == 0 && lexstate.dyd.n_label == 0));
			return funcstate.f;
		}

		// look up and keep at most one copy of each string
		public LuaString newTString(String s) {
			return cachedLuaString(LuaString.valueOf(s));
		}
	
		// look up and keep at most one copy of each string
		public LuaString newTString(LuaString s) {
			return cachedLuaString(s);
		}
	
		public LuaString cachedLuaString(LuaString s) {
			LuaString c = (LuaString) strings.get(s);
			if (c != null) 
				return c;
			strings.put(s, s);
			return s;
		}
	
		public String pushfstring(String string) {
			return string;
		}
	}
}
