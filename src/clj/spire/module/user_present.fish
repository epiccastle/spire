set PWD_LINE (grep "^$NAME:" /etc/passwd; or true)

if test "$PWD_LINE"
  set PARTS (echo "$PWD_LINE" | tr : \n)

  set OUSER_ID "$PARTS[3]"
  set OGID "$PARTS[4]"
  set OCOMMENT "$PARTS[5]"
  set OHOME_DIR "$PARTS[6]"
  set OSHELL "$PARTS[7]"

  # echo 1 $OUSER_ID $USER_ID
  # echo 2 $OGID $GROUP
  # echo 3 $OCOMMENT $COMMENT
  # echo 4 $OHOME_DIR $HOME_DIR
  # echo 5 $OSHELL $SHELL

  set SHADOW (grep "^$NAME:" /etc/shadow | tr : \n)

  set OPASSWORD "$SHADOW[2]"
  set OLAST_CHANGE "$SHADOW[3]"
  set OMIN_AGE "$SHADOW[4]"
  set OMAX_AGE "$SHADOW[5]"
  set OWARNING "$SHADOW[6]"
  set OINACTIVE "$SHADOW[7]"
  set OEXPIRE "$SHADOW[8]"

  # echo "pass: $OPASSWORD"
  # echo "lastchange: $OLAST_CHANGE"
  # echo "minage: $OMIN_AGE"
  # echo "maxage: $OMAX_AGE"
  # echo "warning: $OWARNING"
  # echo "inactive: $OINACTIVE"
  # echo "exp: $OEXPIRE"

  set ARGS ""
  set EXIT 0

  if test "$COMMENT"
      if test "$COMMENT" != "$OCOMMENT"
          set ARGS "$ARGS -c '$COMMENT'"
          set EXIT -1
      end
  end

  if test "$USER_ID"
      if test "$USER_ID" != "$OUSER_ID"
          set ARGS "$ARGS -u '$USER_ID'"
          set EXIT -1
      end
  end

  if test "$HOME_DIR"
      if test "$HOME_DIR" != "$OHOME_DIR"
          set ARGS "$ARGS -d '$HOME_DIR'"
          if test "$OPTS_MOVE_HOME"
              set ARGS "$ARGS -m"
          end
      end
      set EXIT -1
  end

  if test "$GROUP"
      if "$GROUP" != "$OGROUP"
          set ARGS "$ARGS -g '$GROUP'"
          set EXIT -1
      end
  end

  if test "$PASSWORD"
      if "$PASSWORD" != "$OPASSWORD"
          set ARGS "$ARGS -p '$PASSWORD'"
          set EXIT -1
      end
  end

  if test "$SHELL"
      if test "$SHELL" != "$OSHELL"
          set ARGS "$ARGS -s '$SHELL'"
          set EXIT -1
      end
  end

  if test "$ARGS"
      set COMMAND "usermod$ARGS '$NAME'"
  else
      set COMMAND "true"
  end
else
  # user does not exist
  set ARGS ""
  set EXIT 0

  if test "$COMMENT"
    set ARGS "$ARGS -c '$COMMENT'"
    set EXIT -1
  end

  if test "$USER_ID"
    set ARGS "$ARGS -u '$USER_ID'"
    set EXIT -1
  end

  if test "$HOME_DIR"
    set ARGS "$ARGS -d '$HOME_DIR'"
    set EXIT -1
  end

  if test "$GROUP"
    set ARGS "$ARGS -g '$GROUP'"
    set EXIT -1
  end

  if test "$PASSWORD"
    set ARGS "$ARGS -p '$PASSWORD'"
    set EXIT -1
  end

  if test "$SHELL"
    set ARGS "$ARGS -s '$SHELL'"
    set EXIT -1
  end

  set COMMAND "useradd$ARGS '$NAME'"
end

# echo "comm-a:"
# echo "$COMMAND"
eval $COMMAND
exit $EXIT
