CREATE TABLE advisors (
	advisor_id	    SERIAL PRIMARY KEY,
	adviosr_name    TEXT NOT NULL
);

CREATE TABLE model_portfolios (
	model_portfolio_id	SERIAL PRIMARY KEY,
    model_name	        TEXT NOT NULL
);

CREATE TABLE clients (
	client_id			SERIAL PRIMARY KEY,
	client_name			TEXT NOT NULL, 
	advisor_id			INTEGER REFERENCES advisors(advisor_id),
	model_portfolio_id 	INTEGER REFERENCES model_portfolios(model_portfolio_id)
); 

CREATE TABLE instruments (
	instrument_id	    SERIAL PRIMARY KEY,
	instrument_name	    TEXT NOT NULL UNIQUE,
	ticker	            TEXT NOT NULL UNIQUE,
	currency		    TEXT NOT NULL
);

CREATE TABLE model_portfolio_holdings (
	model_portfolio_id	INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id), 
	instrument_id		INTEGER NOT NULL REFERENCES instruments(instrument_id), 
	target_weight_pct 	NUMERIC(5,2) NOT NULL CHECK (target_weight_pct BETWEEN 0 AND 100),
	PRIMARY KEY (model_portfolio_id, instrument_id)
);

CREATE TABLE client_subscriptions (
	client_id 		    INTEGER NOT NULL REFERENCES clients(client_id),
    model_portfolio_id	INTEGER NOT NULL REFERENCES model_portfolios(model_portfolio_id), 
	subscribed_date		DATE NOT NULL,
	PRIMARY KEY (client_id, model_portfolio_id)
);

CREATE TABLE client_holdings (
	client_id 		    INTEGER NOT NULL REFERENCES clients(client_id),
	instrument_id	    INTEGER NOT NULL REFERENCES instruments(instrument_id), 
	quantity		    INTEGER NOT NULL CHECK (quantity > 0), 
	as_of_date		    DATE NOT NULL,
	PRIMARY KEY (client_id, instrument_id)
);

CREATE TABLE client_trades (
	trade_id		SERIAL PRIMARY KEY,
    client_id 		INTEGER NOT NULL REFERENCES clients(client_id),
	instrument_id	INTEGER NOT NULL REFERENCES instruments(instrument_id),
	trade_type		TEXT NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
	quantity		INTEGER NOT NULL CHECK (quantity > 0), 
    price			NUMERIC(10,2) NOT NULL CHECK (price > 0),
	trade_date		DATE NOT NULL
);
