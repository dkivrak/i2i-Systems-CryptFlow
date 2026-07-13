package com.i2i.cryptflow.chat;

import com.i2i.cryptflow.market.*;
import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import com.i2i.cryptflow.trade.TradeTransactionRepository;
import com.i2i.cryptflow.user.UserRepository;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  public static final String DISCLAIMER="Educational purposes only — not financial advice.";
  private final GeminiClient gemini;private final UserRepository users;private final WalletRepository wallets;private final PortfolioAssetRepository assets;
  private final TradeTransactionRepository trades;private final PriceSnapshotRepository snapshots;private final MarketPriceService market;
  public ChatService(GeminiClient g,UserRepository u,WalletRepository w,PortfolioAssetRepository a,TradeTransactionRepository t,PriceSnapshotRepository s,MarketPriceService m){gemini=g;users=u;wallets=w;assets=a;trades=t;snapshots=s;market=m;}
  @Transactional(readOnly=true) public ChatResponse query(UUID userId,String message){
    var user=users.findById(userId).orElseThrow();var wallet=wallets.findByUserId(userId).orElseThrow();
    var prompt=new StringBuilder("You are CryptFlow's educational crypto assistant. Only discuss the supplied account, portfolio, trades, market trends, and educational crypto insights. Never claim certainty or provide financial advice. Answer in the language of the user's question.\n\n")
      .append("USER EMAIL: ").append(user.getEmail()).append("\nUSD BALANCE: ").append(wallet.getUsdBalance()).append("\nPORTFOLIO: ").append(assets.findByWalletIdOrderBySymbol(wallet.getId()).stream().map(a->a.getSymbol()+"="+a.getQuantity()).toList())
      .append("\nCURRENT PRICES: ").append(market.getCurrent()).append("\nRECENT TRADES: ").append(trades.findTop20ByUserIdOrderByExecutedAtDesc(userId).stream().map(t->t.getSide()+" "+t.getQuantity()+" "+t.getSymbol()+" @ "+t.getUnitPriceUsd()).toList());
    for(var symbol:AssetSymbol.values())prompt.append("\n").append(symbol).append(" RECENT PRICES: ").append(snapshots.findTop20BySymbolOrderByRecordedAtDesc(symbol).stream().map(PriceSnapshot::getPriceUsd).toList());
    prompt.append("\n\nUSER QUESTION: ").append(message).append("\nInclude this exact final line: ").append(DISCLAIMER);
    var answer=gemini.generate(prompt.toString());if(!answer.contains(DISCLAIMER))answer=answer+"\n\n"+DISCLAIMER;
    return new ChatResponse(answer,DISCLAIMER,Instant.now());
  }
  public record ChatResponse(String answer,String disclaimer,Instant generatedAt){}
}

