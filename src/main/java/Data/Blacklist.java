package Data;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.groups.MultiCollect;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import org.hibernate.mapping.Set;

import javax.enterprise.context.ApplicationScoped;
import java.text.CollationKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BlackList {

    private String email;
    private int id;

    public BlackList(){

    }

    public int getId(){
        return this.id;
    }

    public void setId(int a){
        this.id = a;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }



    public static Map.Entry<Integer, String> from(Row row){

        BlackList blackList = new BlackList();
        blackList.setId(row.getInteger("id"));
        blackList.setEmail(row.getString("email"));
        Map.Entry<Integer, String> fd = new Map.Entry<>() {
            @Override
            public Integer getKey() {
                return blackList.getId();
            }

            @Override
            public String getValue() {
                return blackList.getEmail();
            }

            @Override
            public String setValue(String value) {
                return null;
            }
        };
//        fd.put(blackList.getId(), blackList.getEmail());
        return fd;
    }


    public static List<Map.Entry<Integer, String>> findAll(PgPool dbclient){
        return   dbclient.query("select * from black_list")
                .execute()
                .onItem()
                .transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem()
                .transform(BlackList::from)
                .collect()
                .asList()
                .await()
                .indefinitely();
    }


}
