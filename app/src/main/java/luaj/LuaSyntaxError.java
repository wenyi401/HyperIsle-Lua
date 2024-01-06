package luaj;

public class LuaSyntaxError extends LuaError {
    public LuaSyntaxError(Throwable cause) {
        super(cause);
    }

    public LuaSyntaxError(String message) {
        super(message);
    }

    public LuaSyntaxError(String message, int level) {
        super(message, level);
    }

    public LuaSyntaxError(LuaValue message_object) {
        super(message_object);
    }
}
