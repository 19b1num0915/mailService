package Data;

import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;

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



    public static HashMap<Integer, String> from(Row row){
        HashMap<Integer, String> fd = new HashMap<>();
        BlackList blackList = new BlackList();
        blackList.setId(row.getInteger("id"));
        blackList.setEmail(row.getString("email"));
        fd.put(blackList.getId(), blackList.getEmail());
        return fd;
    }


    public static List<Row> findAll(PgPool dbclient){



        return   dbclient.query("Select * from black_list")
                .execute()
                .onItem()
                .transformToMulti(set -> Multi.createFrom().iterable(set))
                .collect()
                .asList()
                .await()
                .indefinitely();



    }


}
