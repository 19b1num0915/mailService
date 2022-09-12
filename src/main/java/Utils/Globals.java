package Utils;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class Globals {

    public static Logger LOGGER = Logger.getLogger(Globals.class.getName());

    public static boolean isValidEmail(String email) {
        String ePattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        Pattern p = Pattern.compile(ePattern);
        Matcher m = p.matcher(email);
        return m.matches();
    }

}
