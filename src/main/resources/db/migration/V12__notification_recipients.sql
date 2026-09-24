CREATE TABLE notification_recipient (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(150) NOT NULL,
    description VARCHAR(200),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notification_recipient_email
    ON notification_recipient (LOWER(email));

INSERT INTO notification_recipient (email, description, active)
VALUES ('ecarrasco@tcl.com', 'IT Engineering - Admin', TRUE);
