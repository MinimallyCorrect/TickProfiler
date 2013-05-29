local monitorSide = "left"

if peripheral.isPresent(monitorSide) and peripheral.getType(monitorSide) == "monitor" then
  term.redirect(peripheral.wrap(monitorSide))
else
  print("No monitor found")
  return
end

function explode(inSplitPattern, str)
  local outResults = { }
  local theStart = 1
  local theSplitStart, theSplitEnd = string.find( str, inSplitPattern, theStart )
  while theSplitStart do
  local sub = string.sub( str, theStart, theSplitStart-1 )
    table.insert( outResults,  sub)
    theStart = theSplitEnd + 1
    theSplitStart, theSplitEnd = string.find( str, inSplitPattern, theStart )
  end
  table.insert( outResults, string.sub( str, theStart ) )
  return outResults
end

function printColouredBars(str, first)
  parts = explode("|", str)
  local l = #parts
  for k = 1, l do
    if first then
      term.setTextColor(colors.blue)
    end
    io.write(parts[k])
    if first then
      term.setTextColor(colors.white)
    end
    if k ~= l then
      term.setTextColor(colors.red)
      io.write("|")
      term.setTextColor(colors.white)
    end
  end
end

function profile()
  term.setCursorPos(1, 1)
  local file = fs.open("profile.txt", "r")
  local text = file.readAll()
  file.close()
  local tables = explode("\n\n", text)
  term.clear()
  local i, j
  for i = 1, #tables do
    lines = explode("\n", tables[i] .. "")
    for j = 1, #lines do
      printColouredBars(lines[j] .. "\n", j == 1)
    end
	if i ~= #tables then
		io.write("\n")
	end
  end
end

while true do
  profile()
  sleep(60)
end

term.restore()
