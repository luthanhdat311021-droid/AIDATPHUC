// Minimal Supabase transport adapter for integration tests against real PostgreSQL (PGlite).
// Business rules remain in the unmodified migrations, worker and contract.
export function pgAdapter(pg) {
  const db={ failSettlementOnce:false,
    async rpc(name,args={}) {
      if(name==='studymind_settle_token_job'&&db.failSettlementOnce){db.failSettlementOnce=false;return {error:{code:'TEST_TRANSIENT'}};}
      try{
        const keys=Object.keys(args), sql=`select ${name}(${keys.map((k,i)=>`${k}=>$${i+1}`).join(',')}) as result`;
        return {data:(await pg.query(sql,Object.values(args))).rows[0].result};
      }catch(error){return {error};}
    },
    from(table) {
      let action='select', values, columns='*', filters=[], sort='', limit='', singular=false, optional=false, conflict='';
      const builder={
        select(c='*'){columns=c;return builder;},
        update(v){action='update';values=v;return builder;},
        upsert(v,opts){action='insert';values=v;conflict=opts.onConflict;return builder;},
        eq(k,v){filters.push([k,'=',v]);return builder;},
        is(k,v){filters.push([k,'is',v]);return builder;},
        in(k,v){filters.push([k,'in',v]);return builder;},
        order(k,opts={}){sort=` order by ${k} ${opts.ascending===false?'desc':'asc'}`;return builder;},
        limit(n){limit=` limit ${n}`;return builder;},
        single(){singular=true;return builder;},
        maybeSingle(){singular=true;optional=true;return builder;},
        async then(resolve){
          try{
            const params=[];const bind=v=>{params.push(v);return '$'+params.length;};
            let sql;
            if(action==='insert'){
              sql=`insert into ${table}(${Object.keys(values).join(',')}) values(${Object.values(values).map(bind).join(',')}) on conflict(${conflict}) do nothing returning ${columns}`;
            }else{
              sql=action==='select'?`select ${columns} from ${table}`:`update ${table} set ${Object.entries(values).map(([k,v])=>`${k}=${bind(v)}`).join(',')}`;
              if(filters.length)sql+=' where '+filters.map(([k,op,v])=>op==='in'?`${k} in (${v.map(bind).join(',')})`:op==='is'?`${k} is null`:`${k}=${bind(v)}`).join(' and ');
              sql+=action==='select'?sort+limit:` returning ${columns}`;
            }
            const rows=(await pg.query(sql,params)).rows;
            if(singular&&!optional&&rows.length!==1)return resolve({error:{code:'P0002'}});
            return resolve({data:singular?(rows[0]??null):rows});
          }catch(error){return resolve({error});}
        }
      };return builder;
    }
  };return db;
}
